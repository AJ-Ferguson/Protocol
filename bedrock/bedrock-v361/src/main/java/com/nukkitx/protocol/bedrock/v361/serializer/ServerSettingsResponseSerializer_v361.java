package com.nukkitx.protocol.bedrock.v361.serializer;

import com.nukkitx.network.VarInts;
import com.nukkitx.protocol.bedrock.packet.ServerSettingsResponsePacket;
import com.nukkitx.protocol.bedrock.v361.BedrockUtils;
import com.nukkitx.protocol.serializer.PacketSerializer;
import io.netty.buffer.ByteBuf;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ServerSettingsResponseSerializer_v361 implements PacketSerializer<ServerSettingsResponsePacket> {
    public static final ServerSettingsResponseSerializer_v361 INSTANCE = new ServerSettingsResponseSerializer_v361();


    @Override
    public void serialize(ByteBuf buffer, ServerSettingsResponsePacket packet) {
        VarInts.writeUnsignedInt(buffer, packet.getFormId());
        BedrockUtils.writeString(buffer, packet.getFormData());
    }

    @Override
    public void deserialize(ByteBuf buffer, ServerSettingsResponsePacket packet) {
        packet.setFormId(VarInts.readUnsignedInt(buffer));
        packet.setFormData(BedrockUtils.readString(buffer));
    }
}
