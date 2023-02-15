package io.github.dsheirer.module.decode.tetra;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import io.github.dsheirer.module.decode.DecoderType;
import io.github.dsheirer.module.decode.config.DecodeConfiguration;
import io.github.dsheirer.source.tuner.channel.ChannelSpecification;

public class DecodeConfigTETRA extends DecodeConfiguration {

    public DecodeConfigTETRA()
    {
    }
    @JacksonXmlProperty(isAttribute = true, localName = "type", namespace = "http://www.w3.org/2001/XMLSchema-instance")
    public DecoderType getDecoderType() {
        return DecoderType.TETRA;
    }

    @Override
    @JsonIgnore
    public ChannelSpecification getChannelSpecification() {
        return new ChannelSpecification(100000.0, 25000, 12500.0, 13500.0);
    }
}
