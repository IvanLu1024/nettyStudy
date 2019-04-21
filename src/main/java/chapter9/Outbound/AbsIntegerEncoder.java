package chapter9.Outbound;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

public class AbsIntegerEncoder extends MessageToMessageEncoder<ByteBuf> {



    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        while (in.readableBytes()>=4){
            int value = Math.abs(in.readInt());     //从输入的ByteBuf中读取下一个整数，并计算其绝对值
            out.add(value);     //将该整数的绝对值写入编码消息的List中
        }

    }
}
