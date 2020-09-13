package top.gardel.chess;

import com.google.protobuf.Any;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import top.gardel.chess.proto.AuthInfo;
import top.gardel.chess.proto.CompetitionFinish;
import top.gardel.chess.proto.CompetitionOperation;
import top.gardel.chess.proto.PutChess;
import top.gardel.chess.proto.Response;
import top.gardel.chess.proto.Statistics;

@Getter
@Setter
public class Player {
    private final Channel channel;
    private UUID uuid;
    private State state;
    private Competition competition;

    public Player(UUID uuid, Channel channel) {
        setUuid(uuid);
        state = State.NOT_AUTHED;
        this.channel = channel;
    }

    public void setUuid(UUID uuid) {
        this.uuid = Objects.requireNonNullElseGet(uuid, UUID::randomUUID);
    }

    public void joinCompetition(Competition competition) {
        if (state != State.FREE) throw new IllegalStateException("Already in a competition.");
        this.competition = competition;
        state = State.PLAYING;
    }

    public void leaveCompetition() {
        state = State.FREE;
    }

    public void setCompetition(Competition competition) {
        // DO NOTHING
    }

    public ChannelFuture sendPutChess(boolean mine, int x, int y) {
        if (state == State.PLAYING)
            return channel.writeAndFlush(Response.newBuilder()
                .setBody(Any.pack(PutChess.newBuilder()
                    .setMyself(mine)
                    .setX(x)
                    .setY(y)
                    .build())));
        else return channel.newFailedFuture(new IllegalStateException("not in game"));
    }

    public ChannelFuture sendAuthInfo() {
        if (state == State.NOT_AUTHED)
            return channel.newFailedFuture(new IllegalStateException("not authed"));
        else return channel.writeAndFlush(Response.newBuilder()
            .setBody(Any.pack(AuthInfo.newBuilder()
                .setUuid(uuid.toString())
                .build())));
    }

    public ChannelFuture sendOperationResponse(CompetitionOperation.Operation operation) {
        if (competition != null) {
            var builder = CompetitionOperation.newBuilder()
                .setId(competition.getId())
                .setOperation(operation);
            if (operation == CompetitionOperation.Operation.Join) {
                builder.setPlayerA(AuthInfo.newBuilder()
                    .setUuid(competition.getPlayerA().getUuid().toString()));
            }
            return channel.writeAndFlush(Response.newBuilder()
                .setBody(Any.pack(builder.build())));
        } else return channel.newFailedFuture(new IllegalStateException("competition not found"));
    }

    public ChannelFuture sendFinish() {
        if (competition != null) {
            var builder = CompetitionFinish.newBuilder();
            byte winner = competition.checkWinner();
            switch (winner) {
                case 'A':
                    builder.setWinner(competition.getPlayerA().getUuid().toString());
                    break;
                case 'B':
                    builder.setWinner(competition.getPlayerB().getUuid().toString());
                    break;
                case 'N':
                    builder.setWinner("N");
                    break;
            }
            return channel.writeAndFlush(Response.newBuilder().setBody(Any.pack(builder.build())));
        } else return channel.newFailedFuture(new IllegalStateException("competition not found"));
    }

    public ChannelFuture sendStatistics() {
        if (competition != null) {
            var builder = Statistics.newBuilder();
            if (equals(competition.getPlayerA())) {
                builder.setWinTime(competition.getPlayerAWin())
                    .setLoseTime(competition.getPlayerALose());
            } else if (equals(competition.getPlayerB())) {
                builder.setWinTime(competition.getPlayerBWin())
                    .setLoseTime(competition.getPlayerBLose());
            } else return channel.writeAndFlush(Response.newBuilder().setError("未在对局中").build());
            return channel.writeAndFlush(Response.newBuilder().setBody(Any.pack(builder.build())));
        } else return channel.newFailedFuture(new IllegalStateException("competition not found"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Player)) return false;
        Player player = (Player) o;
        return Objects.equals(getUuid(), player.getUuid());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUuid());
    }

    enum State {
        NOT_AUTHED,
        PLAYING,
        FREE;
    }
}
