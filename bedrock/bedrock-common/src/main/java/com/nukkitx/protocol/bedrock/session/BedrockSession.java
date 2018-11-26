package com.nukkitx.protocol.bedrock.session;

import com.nukkitx.network.NetworkPacket;
import com.nukkitx.network.raknet.RakNetPacket;
import com.nukkitx.network.raknet.session.RakNetSession;
import com.nukkitx.network.util.Preconditions;
import com.nukkitx.protocol.MinecraftSession;
import com.nukkitx.protocol.PlayerSession;
import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.BedrockPacketCodec;
import com.nukkitx.protocol.bedrock.annotation.NoEncryption;
import com.nukkitx.protocol.bedrock.compat.CompatUtils;
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import com.nukkitx.protocol.bedrock.packet.DisconnectPacket;
import com.nukkitx.protocol.bedrock.session.data.AuthData;
import com.nukkitx.protocol.bedrock.session.data.ClientData;
import com.nukkitx.protocol.bedrock.wrapper.DefaultWrapperHandler;
import com.nukkitx.protocol.bedrock.wrapper.WrappedPacket;
import com.nukkitx.protocol.bedrock.wrapper.WrapperHandler;
import com.nukkitx.protocol.util.NativeCodeFactory;
import com.voxelwind.server.jni.hash.VoxelwindHash;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.jni.cipher.BungeeCipher;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

@Getter
public class BedrockSession<PLAYER extends PlayerSession> implements MinecraftSession<PLAYER, RakNetSession> {
    private static final InternalLogger log = InternalLoggerFactory.getInstance(BedrockSession.class);
    private static final ThreadLocal<VoxelwindHash> hashLocal = ThreadLocal.withInitial(NativeCodeFactory.hash::newInstance);
    private static final InetSocketAddress LOOPBACK_BEDROCK = new InetSocketAddress(InetAddress.getLoopbackAddress(), 19132);

    @Getter(AccessLevel.NONE)
    private final Queue<BedrockPacket> currentlyQueued = new ConcurrentLinkedQueue<>();
    @Getter(AccessLevel.NONE)
    private final AtomicLong sentEncryptedPacketCount = new AtomicLong();
    private BedrockPacketCodec packetCodec = CompatUtils.COMPAT_CODEC;
    private BedrockPacketHandler handler;
    private WrapperHandler wrapperHandler = DefaultWrapperHandler.DEFAULT;
    private RakNetSession connection;
    private AuthData authData;
    private ClientData clientData;
    private BungeeCipher encryptionCipher;
    private BungeeCipher decryptionCipher;
    @Setter
    private PLAYER player;
    @Getter(AccessLevel.NONE)
    private byte[] serverKey;
    private int protocolVersion;

    public BedrockSession(RakNetSession connection) {
        this.connection = connection;
    }

    public AuthData getAuthData() {
        return authData;
    }

    public void setAuthData(AuthData authData) {
        Preconditions.checkNotNull(authData, "authData");
        this.authData = authData;
    }

    public void setHandler(@Nonnull BedrockPacketHandler handler) {
        checkForClosed();
        Preconditions.checkNotNull(handler, "handler");
        this.handler = handler;
    }

    void setPacketCodec(BedrockPacketCodec packetCodec) {
        this.packetCodec = packetCodec;
    }

    public void setWrapperHandler(WrapperHandler wrapperHandler) {
        checkForClosed();
        Preconditions.checkNotNull(wrapperHandler, "wrapperCompressionHandler");
        this.wrapperHandler = wrapperHandler;
    }

    private void checkForClosed() {
        Preconditions.checkState(!connection.isClosed(), "Connection has been closed!");
    }

    public void sendPacket(BedrockPacket packet) {
        checkForClosed();
        Preconditions.checkNotNull(packet, "packet");
        if (log.isTraceEnabled()) {
            String to = connection.getRemoteAddress().orElse(LOOPBACK_BEDROCK).toString();
            log.trace("Outbound {}: {}", to, packet);
        }

        // Verify that the packet ID exists.
        packetCodec.getId(packet);

        currentlyQueued.add(packet);
    }

    public void sendPacketImmediately(NetworkPacket packet) {
        checkForClosed();
        Preconditions.checkNotNull(packet, "packet");
        sendPacketInternal(packet);
    }

    private void sendPacketInternal(NetworkPacket packet) {
        if (packet instanceof BedrockPacket) {
            if (log.isTraceEnabled()) {
                String to = connection.getRemoteAddress().orElse(LOOPBACK_BEDROCK).toString();
                log.trace("Outbound {}: {}", to, packet);
            }
            WrappedPacket wrappedPacket = new WrappedPacket();
            wrappedPacket.getPackets().add((BedrockPacket) packet);
            if (packet.getClass().isAnnotationPresent(NoEncryption.class)) {
                wrappedPacket.setEncrypted(true);
            }
            packet = wrappedPacket;
        }

        if (packet instanceof WrappedPacket) {
            WrappedPacket wrappedPacket = (WrappedPacket) packet;
            ByteBuf compressed;
            if (wrappedPacket.getBatched() == null) {
                compressed = wrapperHandler.compressPackets(packetCodec, wrappedPacket.getPackets());
            } else {
                compressed = wrappedPacket.getBatched();
            }

            ByteBuf finalPayload = PooledByteBufAllocator.DEFAULT.directBuffer();
            try {
                if (encryptionCipher == null || wrappedPacket.isEncrypted()) {
                    compressed.readerIndex(0);
                    finalPayload.writeBytes(compressed);
                } else {
                    compressed.readerIndex(0);
                    byte[] trailer = generateTrailer(compressed);
                    compressed.writeBytes(trailer);

                    compressed.readerIndex(0);
                    encryptionCipher.cipher(compressed, finalPayload);
                }
            } catch (GeneralSecurityException e) {
                finalPayload.release();
                throw new RuntimeException("Unable to encrypt package", e);
            } finally {
                compressed.release();
            }
            wrappedPacket.setPayload(finalPayload);
        }

        if (packet instanceof RakNetPacket) {
            connection.sendPacket((RakNetPacket) packet);
        } else {
            throw new UnsupportedOperationException("Unknown packet type sent");
        }
    }

    public void onTick() {
        if (connection.isClosed()) {
            return;
        }

        sendQueued();
    }

    private void sendQueued() {
        BedrockPacket packet;
        WrappedPacket wrapper = new WrappedPacket();
        while ((packet = currentlyQueued.poll()) != null) {
            if (packet.getClass().isAnnotationPresent(NoEncryption.class)) {
                // We hit a wrappable packet. Send the current wrapper and then send the un-wrappable packet.
                if (!wrapper.getPackets().isEmpty()) {
                    sendPacketInternal(wrapper);
                    wrapper = new WrappedPacket();
                }

                sendPacketInternal(packet);

                try {
                    // Delay things a tiny bit
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    log.error("Interrupted", e);
                }
            }

            wrapper.getPackets().add(packet);
        }

        if (!wrapper.getPackets().isEmpty()) {
            sendPacketInternal(wrapper);
        }
    }

    void enableEncryption(byte[] secretKey) {
        checkForClosed();

        serverKey = secretKey;
        byte[] iv = Arrays.copyOf(secretKey, 16);
        SecretKey key = new SecretKeySpec(secretKey, "AES");
        try {
            encryptionCipher = NativeCodeFactory.cipher.newInstance();
            decryptionCipher = NativeCodeFactory.cipher.newInstance();

            encryptionCipher.init(true, key, iv);
            decryptionCipher.init(false, key, iv);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Unable to initialize ciphers", e);
        }

        connection.setUseOrdering(true);
    }

    private byte[] generateTrailer(ByteBuf buf) {
        VoxelwindHash hash = hashLocal.get();
        ByteBuf counterBuf = PooledByteBufAllocator.DEFAULT.directBuffer(8);
        ByteBuf keyBuf = PooledByteBufAllocator.DEFAULT.directBuffer(serverKey.length);
        try {
            counterBuf.writeLongLE(sentEncryptedPacketCount.getAndIncrement());
            keyBuf.writeBytes(serverKey);

            hash.update(counterBuf);
            hash.update(buf);
            hash.update(keyBuf);
            byte[] digested = hash.digest();
            return Arrays.copyOf(digested, 8);
        } finally {
            counterBuf.release();
            keyBuf.release();
        }
    }

    public boolean isEncrypted() {
        return encryptionCipher != null;
    }

    private void close() {
        if (!connection.isClosed()) {
            connection.close();
        }

        // Free native resources if required
        if (encryptionCipher != null) {
            encryptionCipher.free();
        }
        if (decryptionCipher != null) {
            decryptionCipher.free();
        }

        // Make sure the player is closed properly
        if (player != null) {
            player.close();
        }
    }

    public void setClientData(ClientData clientData) {
        this.clientData = clientData;
    }

    void setProtocolVersion(@Nonnegative int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    @Override
    public void disconnect() {
        disconnect(null, true);
    }

    @Override
    public void disconnect(@Nullable String reason) {
        disconnect(reason, false);
    }

    public void disconnect(@Nullable String reason, boolean hideReason) {
        checkForClosed();

        DisconnectPacket packet = packetCodec.createPacket(DisconnectPacket.class);
        if (reason == null || hideReason) {
            packet.setDisconnectScreenHidden(true);
            reason = "disconnect.disconnected";
        } else {
            packet.setKickMessage(reason);
        }
        sendPacketImmediately(packet);

        if (player != null) {
            player.onDisconnect(reason);
        }

        close();
    }

    @Override
    public void onTimeout() {
        player.onTimeout();

        close();
    }

    public void onWrappedPacket(WrappedPacket packet) throws Exception {
        Preconditions.checkNotNull(packet, "packet");
        if (wrapperHandler == null) {
            return;
        }

        ByteBuf wrappedData = packet.getPayload();
        ByteBuf unwrappedData = null;
        try {
            if (isEncrypted()) {
                // Decryption
                unwrappedData = PooledByteBufAllocator.DEFAULT.directBuffer(wrappedData.readableBytes());
                decryptionCipher.cipher(wrappedData, unwrappedData);
                // TODO: Maybe verify the checksum?
                unwrappedData = unwrappedData.slice(0, unwrappedData.readableBytes() - 8);
            } else {
                // Encryption not enabled so it should be readable.
                unwrappedData = wrappedData;
            }

            String to = getRemoteAddress().orElse(LOOPBACK_BEDROCK).toString();
            // Decompress and handle packets
            for (BedrockPacket pk : wrapperHandler.decompressPackets(packetCodec, unwrappedData)) {
                if (log.isTraceEnabled()) {
                    log.trace("Inbound {}: {}", to, pk.toString());
                }
                pk.handle(handler);
            }
        } finally {
            wrappedData.release();
            if (unwrappedData != null && unwrappedData != wrappedData) {
                unwrappedData.release();
            }
        }
    }

    @Override
    public boolean isClosed() {
        return connection.isClosed();
    }
}
