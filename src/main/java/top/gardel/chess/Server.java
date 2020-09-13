package top.gardel.chess;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import top.gardel.chess.proto.Request;

public class Server {
    private static Server INSTANCE;
    private final Map<ChannelId, Player> players;
    private final Map<Integer, Competition> competitions; // <对局号码, 对局>
    private final int port;
    private NioServerSocketChannel serverChannel = null;

    {
        INSTANCE = this;
    }

    public Server(int port) {
        players = new ConcurrentHashMap<>();
        competitions = new ConcurrentHashMap<>();
        this.port = port;
    }

    public void run() {
        // Create event loop groups. One for incoming connections handling and
        // second for handling actual event by workers
        EventLoopGroup serverGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootStrap = new ServerBootstrap();
            bootStrap.group(serverGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new ProtobufVarint32FrameDecoder());
                        p.addLast(new ProtobufDecoder(Request.getDefaultInstance()));

                        p.addLast(new ProtobufVarint32LengthFieldPrepender());
                        p.addLast(new ProtobufEncoder());

                        p.addLast(new ServerHandler(players, competitions));
                    }
                });

            // Bind to port
            serverChannel = (NioServerSocketChannel) bootStrap.bind(port).sync().channel();
            serverChannel.closeFuture().addListener((ChannelFuture future) -> {
                serverGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            });
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean isRunning() {
        return serverChannel != null && serverChannel.isActive();
    }

    public void stop() throws InterruptedException {
        if (!isRunning()) throw new IllegalStateException("Server is not running");
        serverChannel.close().sync();
    }

    public int getPort() {
        return port;
    }

    public ServerChannel getServerChannel() {
        return serverChannel;
    }

    public Map<Integer, Competition> getCompetitions() {
        return competitions;
    }

    public Map<ChannelId, Player> getPlayers() {
        return players;
    }

    public static Server getInstance() {
        return INSTANCE;
    }

}
