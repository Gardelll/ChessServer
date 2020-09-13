package top.gardel.chess.event;

import io.netty.channel.Channel;
import top.gardel.chess.Player;
import top.gardel.chess.proto.AuthInfo;

public class AuthEvent extends ClientEvent<AuthInfo> {

    public AuthEvent(Channel channel, Player player, AuthInfo request) {
        super(channel, player, request);
    }

    public String getPlayerUuid() {
        return getRequest().getUuid();
    }

    @Override
    public Player getPlayer() {
        return null;
    }
}
