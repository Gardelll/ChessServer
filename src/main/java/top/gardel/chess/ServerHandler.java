package top.gardel.chess;

import com.google.protobuf.Any;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.SimpleChannelInboundHandler;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import top.gardel.chess.event.AuthEvent;
import top.gardel.chess.event.ClientEvent;
import top.gardel.chess.event.CompetitionCreateEvent;
import top.gardel.chess.event.CompetitionJoinEvent;
import top.gardel.chess.event.CompetitionLeaveEvent;
import top.gardel.chess.event.CompetitionPutEvent;
import top.gardel.chess.event.CompetitionResetEvent;
import top.gardel.chess.event.EventHandler;
import top.gardel.chess.event.GetStatisticsEvent;
import top.gardel.chess.event.SyncEvent;
import top.gardel.chess.proto.AuthInfo;
import top.gardel.chess.proto.CompetitionOperation;
import top.gardel.chess.proto.GetStatistics;
import top.gardel.chess.proto.Request;
import top.gardel.chess.proto.Response;
import top.gardel.chess.proto.Sync;

public class ServerHandler extends SimpleChannelInboundHandler<Request> {
    private final Map<ChannelId, Player> players;
    private final Map<Integer, Competition> competitions;
    private final App app;

    public ServerHandler(Map<ChannelId, Player> players, Map<Integer, Competition> competitions) {
        this.players = players;
        this.competitions = competitions;
        app = App.getInstance();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        channel.closeFuture().addListener((ChannelFuture future) -> {
            // 从玩家列表中删除
            Player player = players.remove(future.channel().id());
            if (player != null) {
                Competition competition = player.getCompetition();
                if (competition != null) {
                    if (player.equals(competition.getPlayerB())) {
                        // 通知 A 对手下线
                        competition.setPlayerB(null);
                    } else if (player.equals(competition.getPlayerA())) {
                        // 结束对局
                        Optional.ofNullable(competition.getPlayerB())
                            .ifPresent(Player::sendFinish);
                        competitions.remove(competition.getId());
                    }
                }
            }
        });
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Request msg) throws Exception {
        Channel channel = ctx.channel();
        Any body = msg.getBody();
        ClientEvent<?> event = null;
        if (body.is(AuthInfo.class)) {
            AuthInfo authInfo = body.unpack(AuthInfo.class);
            event = new AuthEvent(channel, null, authInfo);
        } else if (body.is(CompetitionOperation.class)) {
            CompetitionOperation competitionOperation = body.unpack(CompetitionOperation.class);
            Player player = players.get(channel.id());
            if (player == null || player.getState() == Player.State.NOT_AUTHED) {
                ctx.writeAndFlush(Response.newBuilder().setError("未注册").build());
                return;
            }
            switch (competitionOperation.getOperation()) {
                case Create: {
                    event = new CompetitionCreateEvent(channel, player, competitionOperation);
                    break;
                }
                case Join: {
                    event = new CompetitionJoinEvent(channel, player, competitionOperation);
                    break;
                }
                case Leave: {
                    event = new CompetitionLeaveEvent(channel, player, competitionOperation);
                    break;
                }
                case Put: {
                    event = new CompetitionPutEvent(channel, player, competitionOperation);
                    break;
                }
                case Reset: {
                    event = new CompetitionResetEvent(channel, player, competitionOperation);
                    break;
                }
                case UNRECOGNIZED:
                default:
                    event = null;
                    break;
            }
        } else if (body.is(GetStatistics.class)) {
            Player player = players.get(channel.id());
            event = new GetStatisticsEvent(channel, player, body.unpack(GetStatistics.class));
        } else if (body.is(Sync.class)) {
            Player player = players.get(channel.id());
            event = new SyncEvent(channel, player, body.unpack(Sync.class));
        }
        if (event != null) getEventHandler(event.getClass()).invoke(app, event);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
        super.channelReadComplete(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        String errMsg = cause.getLocalizedMessage();
        if (ctx.channel().isActive()) ctx.writeAndFlush(Response.newBuilder().setError(errMsg == null ? cause.getClass().getSimpleName() : errMsg ).build());
    }

    @SuppressWarnings("rawtypes")
    private Method getEventHandler(Class<? extends ClientEvent> eventClass) throws NoSuchMethodException {
        var appClass = app.getClass();
        Method[] methods = appClass.getMethods();
        for (Method method : methods) {
            if (method.getAnnotation(EventHandler.class) == null) continue;
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length > 0 && parameterTypes[0].equals(eventClass))
                return method;
        }
        throw new NoSuchMethodException("public void fun(" + eventClass.getName() + " event): Not found.");
    }
}
