package com.nukkitx.protocol.bedrock.packet;

import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class EntityEventPacket extends BedrockPacket {
    protected long runtimeEntityId;
    protected int event;
    protected int data;

    @Override
    public final boolean handle(BedrockPacketHandler handler) {
        return handler.handle(this);
    }
}
