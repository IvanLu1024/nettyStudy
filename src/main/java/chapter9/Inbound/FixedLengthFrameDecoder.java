package chapter9.Inbound;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class FixedLengthFrameDecoder extends ByteToMessageDecoder {

    private final int frameLength;

    public FixedLengthFrameDecoder(int frameLength) {
        if (frameLength<0){
            throw new IllegalArgumentException("frameLength must be a positive integer:"+frameLength);
        }
        this.frameLength = frameLength;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        while (in.readableBytes()>=frameLength){     //检查是否有足够的字节可以被读取，以生成下一个帧
            ByteBuf buf = in.readBytes(frameLength);    //从输入的ByteBuf读取一个固定大小为frameLength的帧
            out.add(buf);       //将该帧添加到已被解码的消息列表中
        }
    }
}
