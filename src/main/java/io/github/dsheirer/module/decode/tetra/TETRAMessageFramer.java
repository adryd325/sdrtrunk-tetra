package io.github.dsheirer.module.decode.tetra;

import io.github.dsheirer.bits.CorrectedBinaryMessage;
import io.github.dsheirer.dsp.psk.pll.IPhaseLockedLoop;
import io.github.dsheirer.dsp.symbol.Dibit;
import io.github.dsheirer.message.IMessage;
import io.github.dsheirer.module.decode.mpt1327.MPT1327Message;
import io.github.dsheirer.sample.Listener;

public class TETRAMessageFramer implements Listener<Dibit> {
    private long mCurrentTime = System.currentTimeMillis();
    private Listener<IMessage> mMessageListener;
    public TETRAMessageFramer(IPhaseLockedLoop phaseLockedLoop, int bitRate) {



    }
    @Override
    public void receive(Dibit dibit) {
    }

    /**
     * Current timestamp or timestamp of incoming message buffers that is continuously updated to as
     * close as possible to the bits processed for the expected baud rate.
     *
     * @return current time
     */
    private long getTimestamp()
    {
        return mCurrentTime;
    }

    /**
     * Sets the current time.  This should be invoked by an incoming message buffer stream.
     *
     * @param currentTime
     */
    public void setCurrentTime(long currentTime)
    {
        mCurrentTime = currentTime;
    }

    public void setListener(Listener<IMessage> listener)
    {
        mMessageListener = listener;
    }

}
