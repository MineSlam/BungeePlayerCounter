package fr.Alphart.BungeePlayerCounter.Servers;

import com.google.gson.Gson;
import fr.Alphart.BungeePlayerCounter.BPC;
import fr.Alphart.BungeePlayerCounter.Servers.Pinger.VarIntStreams.VarIntDataInputStream;
import fr.Alphart.BungeePlayerCounter.Servers.Pinger.VarIntStreams.VarIntDataOutputStream;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.*;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class Pinger implements Runnable {
    private static final Gson gson = new Gson();
    private final InetSocketAddress address;
    private final String parentGroupName;
    private boolean online = false;

    public Pinger(final String parentGroupName, final InetSocketAddress address) {
        this.parentGroupName = parentGroupName;
        this.address = address;
    }

    public boolean isOnline() {
        return online;
    }

    public int getMaxPlayers() {
        return -1;
    }

    @Override
    public void run() {
        try {
            final PingResponse response = ping(address, 1000);
            online = true;
            BPC.debug("Successfully pinged " + parentGroupName + " group, result : " + response);

        } catch (IOException e) {
            if (!(e instanceof ConnectException) && !(e instanceof SocketTimeoutException)) {
                BPC.severe("An unexpected error occurred while pinging " + parentGroupName + " server", e);
            }

            online = false;
        }
    }

    public static PingResponse ping(final InetSocketAddress host, final int timeout) throws IOException {
        try (Socket socket = new Socket()) {
            OutputStream outputStream;
            VarIntDataOutputStream dataOutputStream;
            InputStream inputStream;
            InputStreamReader inputStreamReader;

            socket.setSoTimeout(timeout);

            socket.connect(host, timeout);

            outputStream = socket.getOutputStream();
            dataOutputStream = new VarIntDataOutputStream(outputStream);

            inputStream = socket.getInputStream();
            inputStreamReader = new InputStreamReader(inputStream);

            // Write handshake, protocol=4 and state=1
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            VarIntDataOutputStream handshake = new VarIntDataOutputStream(b);
            handshake.writeByte(0x00);
            handshake.writeVarInt(4);
            handshake.writeVarInt(host.getHostString().length());
            handshake.writeBytes(host.getHostString());
            handshake.writeShort(host.getPort());
            handshake.writeVarInt(1);
            dataOutputStream.writeVarInt(b.size());
            dataOutputStream.write(b.toByteArray());

            // Send ping request
            dataOutputStream.writeVarInt(1);
            dataOutputStream.writeByte(0x00);
            VarIntDataInputStream dataInputStream = new VarIntDataInputStream(inputStream);
            dataInputStream.readVarInt();
            int id = dataInputStream.readVarInt();
            if (id == -1) {
                throw new IOException("Premature end of stream.");
            }

            if (id != 0x00) {
                throw new IOException(String.format("Invalid packetID. Expecting %d got %d", 0x00, id));
            }

            int length = dataInputStream.readVarInt();
            if (length == -1) {
                throw new IOException("Premature end of stream.");
            }

            if (length == 0) {
                throw new IOException("Invalid string length.");
            }

            // Read ping response
            byte[] in = new byte[length];
            dataInputStream.readFully(in);
            String json = new String(in);

            // Send ping packet (to get ping value in ms)
            long now = System.currentTimeMillis();
            dataOutputStream.writeByte(0x09);
            dataOutputStream.writeByte(0x01);
            dataOutputStream.writeLong(now);

            // Read ping value in ms
            dataInputStream.readVarInt();
            id = dataInputStream.readVarInt();
            if (id == -1) {
                throw new IOException("Premature end of stream.");
            }

            if (id != 0x01) {
                throw new IOException(String.format("Invalid packetID. Expecting %d got %d", 0x01, id));
            }

            long pingTime = dataInputStream.readLong();

            synchronized (gson) {
                final PingResponse response = gson.fromJson(json, PingResponse.class);
                response.setTime((int) (now - pingTime));
                dataOutputStream.close();
                outputStream.close();
                inputStreamReader.close();
                inputStream.close();
                socket.close();
                return response;
            }
        }
    }

    @ToString
    public static class PingResponse {
        @Setter @Getter private int time;
    }

    static class VarIntStreams {
        /**
         * Enhanced DataIS which reads VarInt type
         */
        public static class VarIntDataInputStream extends DataInputStream {

            public VarIntDataInputStream(final InputStream is) {
                super(is);
            }

            public int readVarInt() throws IOException {
                int i = 0;
                int j = 0;

                while (true) {
                    int k = readByte();
                    i |= (k & 0x7F) << j++ * 7;

                    if (j > 5)
                        throw new RuntimeException("VarInt too big");

                    if ((k & 0x80) != 128)
                        break;
                }
                return i;
            }

        }

        /**
         * Enhanced DataOS which writes VarInt type
         */
        public static class VarIntDataOutputStream extends DataOutputStream {

            public VarIntDataOutputStream(final OutputStream os) {
                super(os);
            }

            public void writeVarInt(int paramInt) throws IOException {
                while (true) {
                    if ((paramInt & 0xFFFFFF80) == 0) {
                        writeByte(paramInt);
                        return;
                    }

                    writeByte(paramInt & 0x7F | 0x80);
                    paramInt >>>= 7;
                }
            }
        }
    }

}