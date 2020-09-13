package top.gardel.chess.event;

import io.netty.channel.Channel;
import top.gardel.chess.Player;
import top.gardel.chess.proto.CompetitionOperation;

public class CompetitionJoinEvent extends CompetitionEvent {
    public CompetitionJoinEvent(Channel channel, Player player, CompetitionOperation request) {
        super(channel, player, request);
    }
}
