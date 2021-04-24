package top.gardel.chess;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import top.gardel.chess.event.AuthEvent;
import top.gardel.chess.event.CompetitionCreateEvent;
import top.gardel.chess.event.CompetitionJoinEvent;
import top.gardel.chess.event.CompetitionLeaveEvent;
import top.gardel.chess.event.CompetitionPutEvent;
import top.gardel.chess.event.CompetitionResetEvent;
import top.gardel.chess.event.EventHandler;
import top.gardel.chess.event.GetStatisticsEvent;
import top.gardel.chess.event.SyncEvent;
import top.gardel.chess.proto.CompetitionOperation;
import top.gardel.chess.proto.Response;

public class App {
    private static App INSTANCE;
    private final Server server;
    private final Logger logger = Logger.getLogger("App");

    /**
     * 启动服务器
     *
     * @param server 要启动的服务器
     */
    private App(Server server) {
        synchronized (App.class) {
            if (INSTANCE != null)
                throw new IllegalStateException("App 已经实例化");
            this.server = server;
            INSTANCE = this;
            server.run();
        }
    }

    /**
     * 登录回调
     *
     * @param event 登录事件
     */
    @EventHandler
    public void onAuth(AuthEvent event) {
        Player old = server.getPlayers().remove(event.getChannel().id());
        if (old != null) {
            Competition oldCompetition = old.getCompetition();
            if (oldCompetition != null && oldCompetition.getPlayerA().equals(old)) {
                oldCompetition.setPlayerB(null);
                old.sendFinish();
                old.sendOperationResponse(CompetitionOperation.Operation.Leave);
                server.getCompetitions().remove(oldCompetition.getId());
            }
        }
        String uuid = event.getPlayerUuid();
        Player player = new Player(uuid == null || uuid.isEmpty() ? null : UUID.fromString(uuid), event.getChannel());
        player.setState(Player.State.FREE);
        server.getPlayers().put(event.getChannel().id(), player);
        logger.info(String.format("%s 加入游戏", player.getUuid()));
        player.sendAuthInfo();
    }

    public static App getInstance() {
        return INSTANCE;
    }

    /**
     * 创建对局回调
     *
     * @param event 创建对局事件
     */
    @EventHandler
    public void onCompetitionCreate(CompetitionCreateEvent event) {
        Player player = event.getPlayer();
        if (player.getState() == Player.State.PLAYING) {
            event.getChannel().writeAndFlush(Response.newBuilder().setError("已在对局中").build());
            return;
        }
        Competition competition = new Competition(event.getCompetitionId(), player);
        player.joinCompetition(competition);
        player.sendOperationResponse(CompetitionOperation.Operation.Create);
        server.getCompetitions().put(competition.getId(), competition);
        logger.info(String.format("玩家 %s 用数字 %d 创建了对局", player.getUuid(), competition.getId()));
    }

    /**
     * 加入对局回调
     *
     * @param event 加入对局事件
     */
    @EventHandler
    public void onCompetitionJoin(CompetitionJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getState() == Player.State.PLAYING) {
            event.getChannel().writeAndFlush(Response.newBuilder().setError("已在对局中").build());
            return;
        }
        Competition competition = server.getCompetitions().get(event.getCompetitionId());
        if (competition == null) {
            event.getChannel().writeAndFlush(Response.newBuilder().setError("找不到该对局").build());
            return;
        }
        player.joinCompetition(competition);
        player.sendOperationResponse(CompetitionOperation.Operation.Join);
        competition.setPlayerB(player);
        logger.info(String.format("玩家 %s 用数字 %d 加入了对局", player.getUuid(), competition.getId()));
    }

    /**
     * 离开对局回调
     *
     * @param event 离开对局事件
     */
    @EventHandler
    public void onCompetitionLeave(CompetitionLeaveEvent event) {
        Player player = event.getPlayer();
        if (player.getState() != Player.State.PLAYING) {
            event.getChannel().writeAndFlush(Response.newBuilder().setError("不在对局中").build());
            return;
        }
        Competition competition = server.getCompetitions().get(event.getCompetitionId());
        if (competition == null) competition = player.getCompetition();
        if (competition == null) {
            event.getChannel().writeAndFlush(Response.newBuilder().setError("找不到该对局").build());
            return;
        }
        player.sendFinish().addListener((future) -> {
            if (future.isSuccess()) player.leaveCompetition();
        });
        if (player.equals(competition.getPlayerB())) competition.setPlayerB(null);
        else if (player.equals(competition.getPlayerA())) {
            Optional.ofNullable(competition.getPlayerB())
                .ifPresent(player1 -> {
                    player1.sendFinish();
                    player1.leaveCompetition();
                });
            server.getCompetitions().remove(competition.getId());
            logger.info(String.format("对局 %d 已删除", competition.getId()));
        }
        logger.info(String.format("玩家 %s 离开了对局 %d", player.getUuid(), competition.getId()));
    }

    /**
     * 放置棋子回调
     *
     * @param event 放置棋子事件
     */
    @EventHandler
    public void onCompetitionPut(CompetitionPutEvent event) {
        Player player = event.getPlayer();
        if (player.getState() != Player.State.PLAYING) {
            event.getChannel().writeAndFlush(Response.newBuilder().setError("不在对局中").build());
            return;
        }
        Competition competition = server.getCompetitions().get(event.getCompetitionId());
        if (competition == null) {
            event.getChannel().writeAndFlush(Response.newBuilder().setError("找不到该对局").build());
            return;
        }
        if (!event.getRequest().hasPos()) throw new IllegalArgumentException("operation does not has pos");
        if (competition.putChess(player, event.getPosX(), event.getPosY())) {
            byte winner = competition.checkWinner();
            if (winner != 0) {
                competition.getPlayerA().sendFinish();
                competition.getPlayerB().sendFinish();
            }
        }
    }

    /**
     * 同步棋子回调
     *
     * @param event 同步棋子事件
     */
    @EventHandler
    public void onSync(SyncEvent event) {
        Player player = event.getPlayer();
        player.syncChess();
    }

    /**
     * 重置对局回调
     *
     * @param event 重置对局事件
     */
    @EventHandler
    public void onCompetitionReset(CompetitionResetEvent event) {
        Player player = event.getPlayer();
        if (player.getState() != Player.State.PLAYING) {
            event.getChannel().writeAndFlush(Response.newBuilder().setError("不在对局中").build());
            return;
        }
        Competition competition = server.getCompetitions().get(event.getCompetitionId());
        if (competition == null) {
            event.getChannel().writeAndFlush(Response.newBuilder().setError("找不到该对局").build());
            return;
        }
        competition.reset();
    }

    /**
     * 获取得分统计回调
     *
     * @param event 获取得分统计事件
     */
    @EventHandler
    public void onGetStatistics(GetStatisticsEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.getState() == Player.State.NOT_AUTHED) {
            event.getChannel().writeAndFlush(Response.newBuilder().setError("未注册").build());
            return;
        }
        player.sendStatistics();
    }

    public static void main(String[] args) {
        int port = 5544;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        Server server = new Server(port);
        new App(server);
        try {
            server.getServerChannel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
