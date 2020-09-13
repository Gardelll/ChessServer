package top.gardel.chess.event;

import io.netty.channel.Channel;
import lombok.NonNull;
import top.gardel.chess.Player;
import top.gardel.chess.proto.GetStatistics;

public class GetStatisticsEvent extends ClientEvent<GetStatistics> {
    public GetStatisticsEvent(@NonNull Channel channel, Player player, @NonNull GetStatistics request) {
        super(channel, player, request);
    }
}
