package io.github.dsheirer.module.decode.tetra;

import io.github.dsheirer.message.IMessage;
import io.github.dsheirer.sample.Listener;

public class TETRAMessageProcessor implements Listener<IMessage> {
    private Listener<IMessage> mMessageListener;

    public void receive(IMessage message) {
        if (mMessageListener != null) {
            mMessageListener.receive(message);
        }
    }
    public void setMessageListener(Listener<IMessage> listener)
    {
        mMessageListener = listener;
    }

    public void removeMessageListener()
    {
        mMessageListener = null;
    }
}
