package com.nukkitx.protocol.bedrock.v361.serializer;

import com.nukkitx.network.VarInts;
import com.nukkitx.protocol.bedrock.packet.RequestChunkRadiusPacket;
import com.nukkitx.protocol.serializer.PacketSerializer;
import io.netty.buffer.ByteBuf;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RequestChunkRadiusSerializer_v361 implements PacketSerializer<RequestChunkRadiusPacket> {
    public static final RequestChunkRadiusSerializer_v361 INSTANCE = new RequestChunkRadiusSerializer_v361();


    @Override
    public void serialize(ByteBuf buffer, RequestChunkRadiusPacket packet) {
        VarInts.writeInt(buffer, packet.getRadius());
    }

    @Override
    public void deserialize(ByteBuf buffer, RequestChunkRadiusPacket packet) {
        packet.setRadius(VarInts.readInt(buffer));
    }
}
