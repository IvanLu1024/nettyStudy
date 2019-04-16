package discard;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

import java.nio.charset.Charset;

/**
 * 服务端的处理通道
 *
 * 在这里的处理只是简单地打印一下请求，然后抛弃这个请求
 *
 */
public class DiscardServerHandler extends ChannelHandlerAdapter {

    /**
     * 每当收到客户端的请求的时候，这个方法都被调用
     *
     * @param ctx
     * @param msg
     * @throws Exception
     */
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try{
            ByteBuf in= (ByteBuf) msg;
            System.out.print(in.toString(CharsetUtil.UTF_8));
        }finally {

            //抛弃收到的请求
            ReferenceCountUtil.release(msg);

        }
    }

    /**
     * 发生异常的时候会触发这个方法
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();

    }
}
