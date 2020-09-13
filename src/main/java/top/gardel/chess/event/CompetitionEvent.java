package top.gardel.chess.event;

import io.netty.channel.Channel;
import top.gardel.chess.Player;
import top.gardel.chess.proto.CompetitionOperation;

public abstract class CompetitionEvent extends ClientEvent<CompetitionOperation> {

    public CompetitionEvent(Channel channel, Player player, CompetitionOperation request) {
        super(channel, player, request);
    }

    public int getCompetitionId() {
        return getRequest().getId();
    }

}
