package top.gardel.chess.event;

import io.netty.channel.Channel;
import top.gardel.chess.Player;
import top.gardel.chess.proto.CompetitionOperation;

public class CompetitionPutEvent extends CompetitionEvent {
    public CompetitionPutEvent(Channel channel, Player player, CompetitionOperation request) {
        super(channel, player, request);
    }

    public int getPosX() {
        return getRequest().getPos().getX();
    }

    public int getPosY() {
        return getRequest().getPos().getY();
    }
}
