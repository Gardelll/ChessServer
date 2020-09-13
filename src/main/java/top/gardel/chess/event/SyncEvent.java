package top.gardel.chess.event;

import io.netty.channel.Channel;
import lombok.NonNull;
import top.gardel.chess.Player;
import top.gardel.chess.proto.Sync;

public class SyncEvent extends ClientEvent<Sync> {
    public SyncEvent(@NonNull Channel channel, Player player, @NonNull Sync request) {
        super(channel, player, request);
    }
}
