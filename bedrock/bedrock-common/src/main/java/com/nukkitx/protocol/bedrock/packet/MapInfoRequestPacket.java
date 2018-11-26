package com.nukkitx.protocol.bedrock.packet;

import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class MapInfoRequestPacket extends BedrockPacket {
    protected long uniqueMapId;

    @Override
    public final boolean handle(BedrockPacketHandler handler) {
        return handler.handle(this);
    }
}
