package chapter9.exception;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;

import java.util.List;

public class FrameChunkDecoder extends ByteToMessageDecoder {
    private final int maxFrameLength;

    public FrameChunkDecoder(int maxFrameLength) {
        this.maxFrameLength = maxFrameLength;
    }

    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int readableBytes = in.readableBytes();
        if (readableBytes>maxFrameLength){
            //丢弃字节
            in.clear();
            throw new TooLongFrameException();
        }
        //从输入的ByteBuf读取一个新的帧
        ByteBuf buf = in.readBytes(readableBytes);
        //将这个帧封装到解码消息的List中
        out.add(buf);
    }
}
