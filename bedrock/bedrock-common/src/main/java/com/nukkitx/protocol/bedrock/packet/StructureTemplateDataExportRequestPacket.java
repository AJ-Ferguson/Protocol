package com.nukkitx.protocol.bedrock.packet;

import com.flowpowered.math.vector.Vector3i;
import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.data.StructureSettings;
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class StructureTemplateDataExportRequestPacket extends BedrockPacket {
    private String name;
    private Vector3i position;
    private StructureSettings settings;
    private int operation;

    @Override
    public boolean handle(BedrockPacketHandler handler) {
        return handler.handle(this);
    }
}
