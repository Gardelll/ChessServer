package top.gardel.chess.event;

import com.google.protobuf.Message;
import io.netty.channel.Channel;
import lombok.NonNull;
import top.gardel.chess.Player;

public abstract class ClientEvent<T extends Message> {
    private Channel channel;
    private Player player;
    private T request;

    public ClientEvent(@NonNull Channel channel, Player player, @NonNull T request) {
        this.channel = channel;
        this.player = player;
        this.request = request;
    }

    private ClientEvent() {
    }

    public Channel getChannel() {
        return channel;
    }

    public Player getPlayer() {
        return player;
    }

    public T getRequest() {
        return request;
    }
}
