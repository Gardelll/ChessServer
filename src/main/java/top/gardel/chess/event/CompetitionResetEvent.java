package top.gardel.chess.event;

import io.netty.channel.Channel;
import top.gardel.chess.Player;
import top.gardel.chess.proto.CompetitionOperation;

public class CompetitionResetEvent extends CompetitionEvent {
    public CompetitionResetEvent(Channel channel, Player player, CompetitionOperation request) {
        super(channel, player, request);
    }
}
