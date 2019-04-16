package echo.client;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.CharsetUtil;

@ChannelHandler.Sharable
public class EchoClientHandler extends SimpleChannelInboundHandler<ByteBuf> {



    /**
     * 建立连接以后，立即调用该方法
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.writeAndFlush(Unpooled.copiedBuffer("Netty started !", CharsetUtil.UTF_8));

    }

    /**
     * 每收到服务器的一条响应的时候调用
     *
     *
     */
    @Override
    public void channelRead0(ChannelHandlerContext chc, ByteBuf byteBuf) throws Exception {
        System.out.println("Client 接收到了："+byteBuf.toString(CharsetUtil.UTF_8));
    }


    /**
     * 当发生异常的时候调用
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println("Client Exception ~");
        cause.printStackTrace();
        ctx.close();
    }
}
