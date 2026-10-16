package com.lewisgibson.spawnbuildrights.server;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * The Source RCON protocol, as much of it as a test needs: log in, send a command, read the reply.
 * Each packet is a little-endian length, id and type, the payload, and two NUL bytes.
 */
final class Rcon implements Closeable {

    private static final int TYPE_LOGIN = 3;
    private static final int TYPE_COMMAND = 2;

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private int nextId = 1;

    Rcon(String host, int port, String password) throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(30_000);
        in = new DataInputStream(socket.getInputStream());
        out = socket.getOutputStream();
        Packet reply = exchange(TYPE_LOGIN, password);
        if (reply.id == -1) {
            throw new IOException("RCON login refused");
        }
    }

    String command(String command) throws IOException {
        return exchange(TYPE_COMMAND, command).body;
    }

    private Packet exchange(int type, String body) throws IOException {
        int id = nextId++;
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        ByteBuffer packet =
                ByteBuffer.allocate(4 + 4 + 4 + payload.length + 2).order(ByteOrder.LITTLE_ENDIAN);
        packet.putInt(4 + 4 + payload.length + 2)
                .putInt(id)
                .putInt(type)
                .put(payload)
                .put((byte) 0)
                .put((byte) 0);
        out.write(packet.array());
        out.flush();

        byte[] lengthBytes = in.readNBytes(4);
        if (lengthBytes.length < 4) {
            throw new IOException("RCON connection closed");
        }
        int length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        byte[] rest = in.readNBytes(length);
        ByteBuffer reply = ByteBuffer.wrap(rest).order(ByteOrder.LITTLE_ENDIAN);
        int replyId = reply.getInt();
        reply.getInt();
        String text = new String(rest, 8, Math.max(0, length - 10), StandardCharsets.UTF_8);
        return new Packet(replyId, text);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private record Packet(int id, String body) {}
}
