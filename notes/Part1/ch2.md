# 第 2 章  笔记

利用Netty来构建如下图所示的Echo客户端和服务器应用程序，即客户端在和服务器建立连接以后，发生消息，反过来，服务器又会将这个消息回送给客户端，是典型的“请求-响应交互”模型。

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g24rjqe3zrj20dv092dgb.jpg"/>

## 服务器

### ChannelHandler

这里我们会继承ChannelInboundHandlerAdapter类，并复写其中的一些方法：

- channelRead()：在收到客户端的请求的时候会调用该方法；
- channelReadComplete()：当前批量读取中的最后一条消息调用该方法；
- exceptionCaught()：出现异常的时候会调用

具体代码如下：

```java
@ChannelHandler.Sharable
public class EchoServerHandler extends ChannelInboundHandlerAdapter {

    /**
     * 对于每个传入的消息都会调用
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //将接收到的消息打印出来，并将该消息重新写给发送者
        ByteBuf in = (ByteBuf) msg;
        System.out.println("Server received："+in.toString(CharsetUtil.UTF_8));
        ctx.write(in);

    }

    /**
     *  通知ChannelInboundHandler最后一次对channelRead()的调用是当前批量读取中的最后一条消息
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        //将未发送完的消息冲刷到远程节点，并且关闭该channel
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER)
                .addListener(ChannelFutureListener.CLOSE);

    }

    /**
     * 在读取操作期间，有异常抛出会调用
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println("exception ~");
        cause.printStackTrace();
        ctx.close();
    }
}
```

### 引导服务器

主要逻辑如下：

- 绑定到服务器上的某个端口，监听并接受传入请求；
- 配置Channel，将入站消息通知给EchoServerHandler的实例。



具体代码如下：

```java
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
```

## 客户端

客户端的逻辑如下：

1. 连接到服务器；
2. 发生消息；
3. 对于每个消息，等待并接收服务器响应的相同消息；
4. 关闭连接。

### ChannelHandler

这里将继承SimpleChannelInboundHandler，并复写下列方法：

- channelActive()：连接建立以后就被调用
- channelRead0()：每收到一条来自服务器的消息时就被调用；
- exceptionCaught()：发生异常的时候调用。

具体代码如下：

```java
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
```

### 引导

与服务器的引导相类似，具体代码如下：

```java
public class EchoClient {

    private String host;
    private int port;

    public EchoClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() throws Exception{
        EventLoopGroup group=new NioEventLoopGroup();
        try {
            //创建Bootstrap
            Bootstrap b = new Bootstrap();

            //指定 EventLoopGroup 以处理客户端事件；需要适用于 NIO 的实现
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .remoteAddress(new InetSocketAddress(host,port))
                    .handler(new ChannelInitializer<SocketChannel>() {
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new EchoClientHandler());
                        }
                    });

            //连接到远程节点，阻塞等待直到连接完成
            ChannelFuture f = b.connect().sync();
            //阻塞，直到channel关闭
            f.channel().closeFuture().sync();


        }finally {
            //关闭线程池并且释放所有资源
            group.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length!=2){
            System.err.println("\"Usage: \" + EchoClient.class.getSimpleName() +\n" +
                    "\" <host> <port>\"");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        new EchoClient(host,port).start();

    }
}
```

## 测试结果

服务器：

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g24sgkn8hyj20h303gaa4.jpg"/>

客户端：

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g24sgwafktj20dn0450sw.jpg"/>



## 补充： Discard型服务器

注意，这里Maven中需要引入依赖如下：

```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-all</artifactId>
    <version>5.0.0.Alpha2</version>
</dependency>
```



这种类型的服务器，就是指服务器只接收客户端的消息，而不对客户端进行响应，客户端可以使用**telnet**来模拟。

### ChannelHandler

代码如下：

```java
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
```

### 引导

代码如下：

```java
/**
 *
 * 启动服务管道处理
 */
public class DiscardServer {

    private int port;

    public DiscardServer(int port) {
        super();
        this.port = port;
    }

    public void run() throws Exception {
        /***
         * NioEventLoopGroup 是用来处理I/O操作的多线程事件循环器，
         * Netty提供了许多不同的EventLoopGroup的实现用来处理不同传输协议。 在这个例子中我们实现了一个服务端的应用，
         * 因此会有2个NioEventLoopGroup会被使用。 第一个经常被叫做‘boss’，用来接收进来的连接。
         * 第二个经常被叫做‘worker’，用来处理已经被接收的连接， 一旦‘boss’接收到连接，就会把连接信息注册到‘worker’上。
         * 如何知道多少个线程已经被使用，如何映射到已经创建的Channels上都需要依赖于EventLoopGroup的实现，
         * 并且可以通过构造函数来配置他们的关系。
         */
        EventLoopGroup bossGroup=new NioEventLoopGroup();
        EventLoopGroup workerGroup=new NioEventLoopGroup();

        System.out.println("准备启动的端口是："+port);

        try{
            //一个启动NIO服务的辅助启动类 你可以在这个服务中直接使用Channel
            ServerBootstrap b = new ServerBootstrap();

            //必须进行设置
            b=b.group(bossGroup,workerGroup);

            //ServerSocketChannel以NIO的selector为基础进行实现的，用来接收新的连接
            //这里告诉Channel如何获取新的连接
            b.channel(NioServerSocketChannel.class);

            /***
             * 这里的事件处理类经常会被用来处理一个最近的已经接收的Channel。
             * ChannelInitializer是一个特殊的处理类，
             * 其目的是帮助使用者配置一个新的Channel。
             * 也许你想通过增加一些处理类比如NettyServerHandler来配置一个新的Channel
             * 或者其对应的ChannelPipeline来实现你的网络程序。
             *
             * 当你的程序变的复杂时，可能你会增加更多的处理类到pipline上，然后提取这些匿名类到最顶层的类上。
             */
            b.childHandler(new ChannelInitializer<SocketChannel>() {
                protected void initChannel(SocketChannel socketChannel) throws Exception {
                    socketChannel.pipeline().addLast(new DiscardServerHandler()).addLast(new DiscardServerHandler());
                }
            });

            /***
             * 你可以设置这里指定的通道实现的配置参数。 我们正在写一个TCP/IP的服务端，
             * 因此我们被允许设置socket的参数选项比如tcpNoDelay和keepAlive。
             * 请参考ChannelOption和详细的ChannelConfig实现的接口文档以此可以对ChannelOptions的有一个大概的认识。
             */
            b.option(ChannelOption.SO_BACKLOG,128);

            /***
             * option()是提供给NioServerSocketChannel用来接收进来的连接。
             * childOption()是提供给由父管道ServerChannel接收到的连接，
             * 在这个例子中也是NioServerSocketChannel。
             */
            b.childOption(ChannelOption.SO_KEEPALIVE,true);

            /***
             * 绑定端口并启动去接收进来的连接
             */
            ChannelFuture f = b.bind(port).sync();

            //这里会一直等待，直到socket被关闭
            f.channel().closeFuture().sync();

        }finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }


    public static void main(String[] args) throws Exception {
        int port;
        if (args.length>0){
            port=Integer.parseInt(args[0]);
        }else {
            port=8080;
        }

        new DiscardServer(port).run();
        System.out.println("server is running: ");

    }


}
```

测试：

使用telnet模拟客户端来测试，启动服务器

```cmd
telnet 127.0.0.1 9999
```

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g24t6uuc7jj20e905faa6.jpg"/>



