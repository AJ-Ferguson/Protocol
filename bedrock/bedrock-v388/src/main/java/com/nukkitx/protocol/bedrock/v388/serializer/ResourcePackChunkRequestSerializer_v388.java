package com.nukkitx.protocol.bedrock.v388.serializer;

import com.nukkitx.protocol.bedrock.packet.ResourcePackChunkRequestPacket;
import com.nukkitx.protocol.bedrock.v388.BedrockUtils;
import com.nukkitx.protocol.serializer.PacketSerializer;
import io.netty.buffer.ByteBuf;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ResourcePackChunkRequestSerializer_v388 implements PacketSerializer<ResourcePackChunkRequestPacket> {
    public static final ResourcePackChunkRequestSerializer_v388 INSTANCE = new ResourcePackChunkRequestSerializer_v388();


    @Override
    public void serialize(ByteBuf buffer, ResourcePackChunkRequestPacket packet) {
        BedrockUtils.writeString(buffer, packet.getPackId().toString());
        buffer.writeIntLE(packet.getChunkIndex());
    }

    @Override
    public void deserialize(ByteBuf buffer, ResourcePackChunkRequestPacket packet) {
        packet.setPackId(UUID.fromString(BedrockUtils.readString(buffer)));
        packet.setChunkIndex(buffer.readIntLE());
    }
}
