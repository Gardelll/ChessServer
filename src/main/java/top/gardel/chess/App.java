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

    public App(Server server) {
        this.server = server;
    }

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
            if(future.isSuccess()) player.leaveCompetition();
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

    @EventHandler
    public void onSync(SyncEvent event) {
        Player player = event.getPlayer();
        player.syncChess();
    }

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
        server.run();
        INSTANCE = new App(server);
        try {
            server.getServerChannel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static App getInstance() {
        return INSTANCE;
    }
}
