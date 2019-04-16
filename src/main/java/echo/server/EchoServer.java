package echo.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;

public class EchoServer {

    private final int port;

    public EchoServer(int port) {
        this.port = port;
    }


    public void start() throws Exception{
        final EchoServerHandler serverHandler=new EchoServerHandler();
        EventLoopGroup group=new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            //指定所使用的NIO传输channel
            b.group(group).channel(NioServerSocketChannel.class)
                    .localAddress(new InetSocketAddress(port))  //使用指定的端口设置套接字地址
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(serverHandler);
                        }
                    });
            //异步地绑定服务；调用sync()阻塞等待直到绑定完成
            ChannelFuture f = b.bind().sync();
            //阻塞当前线程直到它完成
            f.channel().closeFuture().sync();


        }finally {
            group.shutdownGracefully().sync();
        }

    }

    public static void main(String[] args) throws Exception {
        if (args.length!=1){
            System.err.println("args error!");
        }
        int port = Integer.parseInt(args[0]);
        new EchoServer(port).start();

    }
}
