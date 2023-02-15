package io.github.dsheirer.module.decode.tetra;

import io.github.dsheirer.dsp.filter.FilterFactory;
import io.github.dsheirer.dsp.filter.design.FilterDesignException;
import io.github.dsheirer.dsp.filter.fir.FIRFilterSpecification;
import io.github.dsheirer.dsp.filter.fir.real.IRealFilter;
import io.github.dsheirer.dsp.gain.complex.ComplexGainFactory;
import io.github.dsheirer.dsp.gain.complex.IComplexGainControl;
import io.github.dsheirer.dsp.psk.DQPSKGardnerDemodulator;
import io.github.dsheirer.dsp.psk.InterpolatingSampleBuffer;
import io.github.dsheirer.dsp.psk.pll.CostasLoop;
import io.github.dsheirer.dsp.psk.pll.FrequencyCorrectionSyncMonitor;
import io.github.dsheirer.dsp.psk.pll.PLLBandwidth;
import io.github.dsheirer.dsp.squelch.PowerMonitor;
import io.github.dsheirer.dsp.symbol.Dibit;
import io.github.dsheirer.dsp.symbol.DibitToByteBufferAssembler;
import io.github.dsheirer.dsp.window.WindowType;
import io.github.dsheirer.identifier.IdentifierUpdateListener;
import io.github.dsheirer.identifier.IdentifierUpdateNotification;
import io.github.dsheirer.module.decode.DecoderType;
import io.github.dsheirer.module.decode.FeedbackDecoder;
import io.github.dsheirer.module.decode.p25.phase2.P25P2MessageProcessor;
import io.github.dsheirer.sample.Broadcaster;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.sample.buffer.IByteBufferProvider;
import io.github.dsheirer.sample.complex.ComplexSamples;
import io.github.dsheirer.sample.complex.IComplexSamplesListener;
import io.github.dsheirer.source.ISourceEventListener;
import io.github.dsheirer.source.ISourceEventProvider;
import io.github.dsheirer.source.SourceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class TETRADecoder extends FeedbackDecoder implements ISourceEventListener, ISourceEventProvider,
        IComplexSamplesListener, Listener<ComplexSamples>, IByteBufferProvider, IdentifierUpdateListener {

    private final static Logger mLog = LoggerFactory.getLogger(TETRADecoder.class);

    protected InterpolatingSampleBuffer mInterpolatingSampleBuffer;
    protected DQPSKGardnerDemodulator mQPSKDemodulator;
    protected CostasLoop mCostasLoop;
    protected TETRAMessageFramer mMessageFramer;
    protected TETRAMessageProcessor mMessageProcessor;
    protected IComplexGainControl mAGC = ComplexGainFactory.getComplexGainControl();
    protected PowerMonitor mPowerMonitor = new PowerMonitor();

    private static final int SYMBOL_RATE = 18000;
    protected static final float SAMPLE_COUNTER_GAIN = 0.3f;
    private double mSampleRate;
    private Broadcaster<Dibit> mDibitBroadcaster = new Broadcaster<>();
    private DibitToByteBufferAssembler mByteBufferAssembler = new DibitToByteBufferAssembler(300);

    private Map<Double, float[]> mBasebandFilters = new HashMap<>();
    protected IRealFilter mIBasebandFilter;
    protected IRealFilter mQBasebandFilter;

    private FrequencyCorrectionSyncMonitor mFrequencyCorrectionSyncMonitor;


    public TETRADecoder(DecodeConfigTETRA decodeConfigTETRA) {
        setSampleRate(50000.0);
        mMessageProcessor = new TETRAMessageProcessor();
        mMessageProcessor.setMessageListener(getMessageListener());
        getDibitBroadcaster().addListener(mByteBufferAssembler);
    }

    public void setSampleRate(double sampleRate) {
        mIBasebandFilter = FilterFactory.getRealFilter(getBasebandFilter());
        mQBasebandFilter = FilterFactory.getRealFilter(getBasebandFilter());

        mSampleRate = sampleRate;

        mCostasLoop = new CostasLoop(getSampleRate(), getSymbolRate());
        mCostasLoop.setPLLBandwidth(PLLBandwidth.BW_300);

        mInterpolatingSampleBuffer = new InterpolatingSampleBuffer(getSamplesPerSymbol(), SAMPLE_COUNTER_GAIN);
        mQPSKDemodulator = new DQPSKGardnerDemodulator(mCostasLoop, mInterpolatingSampleBuffer);

        if (mMessageFramer != null) {
            getDibitBroadcaster().removeListener(mMessageFramer);
        }

        //The Costas Loop receives symbol-inversion correction requests when detected.
        //The PLL gain monitor receives sync detect/loss signals from the message framer
        mMessageFramer = new TETRAMessageFramer(mCostasLoop, DecoderType.TETRA.getProtocol().getBitRate());

        mFrequencyCorrectionSyncMonitor = new FrequencyCorrectionSyncMonitor(mCostasLoop, this);
//        mMessageFramer.setSyncDetectListener(mFrequencyCorrectionSyncMonitor);
        mMessageFramer.setListener(getMessageProcessor());

//        mMessageFramer.setSampleRate(sampleRate);
        mPowerMonitor.setSampleRate((int) sampleRate);

        mQPSKDemodulator.setSymbolListener(getDibitBroadcaster());
        getDibitBroadcaster().addListener(mMessageFramer);
    }

    TETRAMessageProcessor getMessageProcessor() {
        return mMessageProcessor;
    }

    @Override
    public DecoderType getDecoderType() {
        return DecoderType.TETRA;
    }

    /**
     * Primary method for processing incoming complex sample buffers
     *
     * @param samples containing channelized complex samples
     */
    @Override
    public void receive(ComplexSamples samples) {
        mMessageFramer.setCurrentTime(System.currentTimeMillis());

        float[] i = mIBasebandFilter.filter(samples.i());
        float[] q = mQBasebandFilter.filter(samples.q());

        //Process the buffer for power measurements
        mPowerMonitor.process(i, q);

        ComplexSamples amplified = mAGC.process(i, q, samples.timestamp());
        mQPSKDemodulator.receive(amplified);
    }


    /**
     * Constructs a baseband filter for this decoder using the current sample rate
     */
    private float[] getBasebandFilter() {
        //Attempt to reuse a cached (ie already-designed) filter if available
        float[] filter = mBasebandFilters.get(getSampleRate());

        if(filter == null)
        {
            filter = FilterFactory.getLowPass(50000, 11250, 13000, 60,
                    WindowType.HANN, true);

            mBasebandFilters.put(getSampleRate(), filter);
        }

        return filter;
    }

    @Override
    public void setSourceEventListener(Listener<SourceEvent> listener) {
        super.setSourceEventListener(listener);
        mPowerMonitor.setSourceEventListener(listener);
    }

    @Override
    public void removeSourceEventListener() {
        super.removeSourceEventListener();
        mPowerMonitor.setSourceEventListener(null);
    }

    @Override
    public Listener<SourceEvent> getSourceEventListener() {
        return sourceEvent -> {
            switch (sourceEvent.getEvent()) {
                case NOTIFICATION_SAMPLE_RATE_CHANGE -> {
                    mCostasLoop.reset();
                    setSampleRate(sourceEvent.getValue().doubleValue());
                }
                case NOTIFICATION_FREQUENCY_CORRECTION_CHANGE ->
                    //Reset the PLL if/when the tuner PPM changes so that we can re-lock
                        mCostasLoop.reset();
            }
        };
    }

    /**
     * Implements the IByteBufferProvider interface - delegates to the byte buffer assembler
     */
    @Override
    public void setBufferListener(Listener<ByteBuffer> listener) {
        mByteBufferAssembler.setBufferListener(listener);
    }

    /**
     * Implements the IByteBufferProvider interface - delegates to the byte buffer assembler
     */
    @Override
    public void removeBufferListener(Listener<ByteBuffer> listener) {
        mByteBufferAssembler.removeBufferListener(listener);
    }

    /**
     * Implements the IByteBufferProvider interface - delegates to the byte buffer assembler
     */
    @Override
    public boolean hasBufferListeners() {
        return mByteBufferAssembler.hasBufferListeners();
    }

    /**
     * Assembler for packaging Dibit stream into reusable byte buffers.
     */
    protected Broadcaster<Dibit> getDibitBroadcaster() {
        return mDibitBroadcaster;
    }

    /**
     * Resets this decoder to prepare for processing a new channel
     */
    @Override
    public void reset() {
        mCostasLoop.reset();
    }

    @Override
    public Listener<ComplexSamples> getComplexSamplesListener() {
        return TETRADecoder.this;
    }

    @Override
    public Listener<IdentifierUpdateNotification> getIdentifierUpdateListener() {
        return identifierUpdateNotification -> {
            /* NOOP */
        };
    }

    protected double getSymbolRate() {
        return SYMBOL_RATE;
    }

    /**
     * Current sample rate for this decoder
     */
    protected double getSampleRate() {
        return mSampleRate;
    }

    /**
     * Samples per symbol based on current sample rate and symbol rate.
     */
    public float getSamplesPerSymbol() {
        return (float) (getSampleRate() / getSymbolRate());
    }
}
