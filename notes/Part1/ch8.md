# 第八章 笔记——引导

引导是负责将 ChannelPipeline、ChannelHandler 和 EventLoop这三个部分组织起来的，指对一个应用程序进行配置并使它运行起来。

## 一、Bootstrap 类

其结构如下所示：

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g2865ps7ncj20fh065aa4.jpg"/>

- 对于服务器来说，使用一个父Channel来接受来自客户端的连接，并创建子Channel用来他们之间的通信；
- 而对于客户端来说，最可能只需要一个单独的、没有父Channel的Channel来用于所有的网络交互（这也适用于无连接的传输协议，例如UDP，因为它们并不是每个连接都需要一个单独的Channel）

## 二、引导客户端和无连接协议

Bootstrap类被用于引导客户端和无连接协议，负责为它们创建Channel。其引导过程如图所示：

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g2878dclk6j20fp07wmxz.jpg"/>

一个使用NIO TCP传输的客户端的引导代码如下：

```java
EventLoopGroup group = new NioEventLoopGroup();
Bootstrap bootstrap = new Bootstrap();
bootstrap.group(group)
	.channel(NioSocketChannel.class)
	.handler(new SimpleChannelInboundHandler<ByteBuf>() {
    @Override
    protected void channeRead0(ChannelHandlerContext channelHandlerContext,ByteBuf byteBuf) throws Exception {
    System.out.println("Received data");
        }
    } );
ChannelFuture future = bootstrap.connect(
new InetSocketAddress("www.manning.com", 80));	//连接到远程主机
future.addListener(new ChannelFutureListener() {
@Override
    public void operationComplete(ChannelFuture channelFuture)
    throws Exception {
    if (channelFuture.isSuccess()) {
    System.out.println("Connection established");
    } else {
    System.err.println("Connection attempt failed");
    channelFuture.cause().printStackTrace();
    }
    }
    } );
```

引导过程中，在调用bind()或者connect()方法之前，必须调用以下方法设置相关组件：

- group();
- channel()或channelFactory();
- handler()

**否则将会导致IllegalStateException。**

## 三、引导服务器

ServerBootstrap类负责服务器引导的过程，它在调用bind()方法调用时将创建一个ServerChannel，并且该ServerChannel管理多个字Channel，如下图所示：

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g287pe6679j20j806v3z7.jpg"/>

示例代码如下：

```java
NioEventLoopGroup group = new NioEventLoopGroup();
ServerBootstrap bootstrap = new ServerBootstrap();
bootstrap.group(group)
        .channel(NioServerSocketChannel.class)
        .childHandler(new SimpleChannelInboundHandler<ByteBuf>() {	//设置相应的入站handler
    @Override
    protected void channelRead0(ChannelHandlerContext ctx ,ByteBuf byteBuf) throws Exception {
    		System.out.println("Received data");
    	}
    } );
    ChannelFuture future = bootstrap.bind(new InetSocketAddress(8080));		//为配置好的实例绑定该Channel
    future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture)
            throws Exception {
            if (channelFuture.isSuccess()) {
            System.out.println("Server bound");
            } else {
            System.err.println("Bound attempt failed");
            channelFuture.cause().printStackTrace();
            }
          }
    } );
```

## 四、从Channel引导客户端

假设你的服务器正在处理一个客户端的请求，这个请求需要它充当第三方系统的客户端。当一个应用程序（如一个代理服务器）必须要和组织现有的系统（如 Web 服务或者数据库）集成时，就可能发生这种情况。在这种情况下，将需要从已经被接受的子 Channel 中引导一个客户端 Channel。

还可以引导客户端的方式来创建新的Bootstrap实例，但这不是一个高效的办法，因为它将要求你**为每个新创建的客户端 Channel 定义另一个 EventLoop。**这会产生额外的线程，以及在已被接受的子 Channel 和客户端 Channel 之间交换数据时不可避免的上下文切换。

一个更好的解决方案是：通过将已被接受的子Channel 的 EventLoop 传递给 Bootstrap的 group()方法来共享该 Event-Loop。**因为分配给 EventLoop 的所有 Channel 都使用同一个线程，所以这避免了额外的线程创建，以及前面所提到的相关的上下文切换，**如下图所示：

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g2881h9pslj20mt0b876e.jpg"/>

示例代码如下：

```java
ServerBootstrap bootstrap = new ServerBootstrap();
bootstrap.group(new NioEventLoopGroup(), new NioEventLoopGroup())
    .channel(NioServerSocketChannel.class)
    .childHandler(		//设置用于处理已被接收的子Channel
    new SimpleChannelInboundHandler<ByteBuf>() {
    ChannelFuture connectFuture;
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.channel(NioSocketChannel.class).handler( new SimpleChannelInboundHandler<ByteBuf>() {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
   	 		System.out.println("Received data");
    	}
    } );
    bootstrap.group(ctx.channel().eventLoop());		//使用与分配给已被接受的子Channel相同的EventLoop
    connectFuture = bootstrap.connect(new InetSocketAddress("www.manning.com", 80));
    }
    @Override
    protected void channelRead0(
    ChannelHandlerContext channelHandlerContext,
    ByteBuf byteBuf) throws Exception {
        if (connectFuture.isDone()) {
        // do something with the data
    	}
    }
    } );
ChannelFuture future = bootstrap.bind(new InetSocketAddress(8080));
future.addListener(new ChannelFutureListener() {
@Override
public void operationComplete(ChannelFuture channelFuture) throws Exception {
    if (channelFuture.isSuccess()) {
    	System.out.println("Server bound");
    } else {
    	System.err.println("Bind attempt failed");
    	channelFuture.cause().printStackTrace();
    }
    }
} );
```

> handler() 方法和 childHandler() 方法之间的区别是：前者所添加的 ChannelHandler 由接受子 Channel 的 ServerChannel 处理，而childHandler() 方法所添加的 ChannelHandler 将由已被接受的子 Channel处理，其代表一个绑定到远程节点的套接字。

当引导过程需要添加很多ChannelHandler，可以使用ChannelInitializer类的initChannel方法，这个方法提供了一种将多个ChannelHandler添加到一个 ChannelPipeline 中的简便方法。你只需要简单地向 Bootstrap 或 ServerBootstrap 的实例提供你的 ChannelInitializer 实现即可，并且一旦 Channel 被注册到了它的 EventLoop 之后，就会调用你的initChannel()版本。在该方法返回之后，ChannelInitializer 的实例将会从 ChannelPipeline 中移除它自己。示例代码如下：

```java
ServerBootstrap bootstrap = new ServerBootstrap();
bootstrap.group(new NioEventLoopGroup(), new NioEventLoopGroup())
        .channel(NioServerSocketChannel.class)
        .childHandler(new ChannelInitializerImpl());
        ChannelFuture future = bootstrap.bind(new InetSocketAddress(8080));
        future.sync();
//设置ChannelInitializer 的实现
final class ChannelInitializerImpl extends ChannelInitializer<Channel> { ②
    @Override
    protected void initChannel(Channel ch) throws Exception {
    ChannelPipeline pipeline = ch.pipeline();
    pipeline.addLast(new HttpClientCodec());
    pipeline.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
    }
}
```

## 五、使用ChannelOption 和属性

Netty中可以使用option()方法来将 ChannelOption 应用到引导。你所提供的值将会被自动应用到引导所创建的所有 Channel。

Netty 还提供了AttributeMap 抽象（一个由 Channel 和引导类提供的集合）以及 AttributeKey<T>（一个用于插入和获取属性值的泛型类）。使用这些工具，便可以安全地将任何类型的数据项与客户端和服务器 Channel（包含 ServerChannel 的子 Channel）相关联了。示例代码如下：

```java
final AttributeKey<Integer> id = new AttributeKey<Integer>("ID");
Bootstrap bootstrap = new Bootstrap();
bootstrap.group(new NioEventLoopGroup())
.channel(NioSocketChannel.class)
.handler(
new SimpleChannelInboundHandler<ByteBuf>() {
@Override
public void channelRegistered(ChannelHandlerContext ctx)
throws Exception {
Integer idValue = ctx.channel().attr(id).get();
// do something with the idValue
}
@Override
protected void channelRead0(ChannelHandlerContext channelHandlerContext,ByteBuf byteBuf) throws Exception {
		System.out.println("Received data");
		}
	}
);
bootstrap.option(ChannelOption.SO_KEEPALIVE,true)		//设置ChannelOption 
		.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
bootstrap.attr(id, 123456);
ChannelFuture future = bootstrap.connect(new InetSocketAddress("www.manning.com", 80));
future.syncUninterruptibly();
```

## 六、引导无连接协议

与基于TCP协议的区别在于，不再调用connect()方法，而只是调用bind()方法，示例代码如下：

```java
Bootstrap bootstrap = new Bootstrap();
bootstrap.group(new OioEventLoopGroup()).channel(
OioDatagramChannel.class).handler(
new SimpleChannelInboundHandler<DatagramPacket>(){
@Override
public void channelRead0(ChannelHandlerContext ctx,
DatagramPacket msg) throws Exception {
	// Do something with the packet
		}
	}
);
ChannelFuture future = bootstrap.bind(new InetSocketAddress(0));	//由于是无连接协议仅仅需要调用bind()方法
future.addListener(new ChannelFutureListener() {
@Override
public void operationComplete(ChannelFuture channelFuture) throws Exception {
if (channelFuture.isSuccess()) {
	System.out.println("Channel bound");
} else {
	System.err.println("Bind attempt failed");
	channelFuture.cause().printStackTrace();
	}
}
});
```

## 七、关闭

Netty中提供了一种优雅关闭EventLoopGroup的方法——shutdownGracefully()方法，该方法的作用是将会返回一个 Future，这个 Future 将在关闭完成时接收到通知。**需要注意的是，shutdownGracefully()方法也是一个异步的操作，所以你需要阻塞等待直到它完成，或者向所返回的 Future 注册一个监听器以在关闭完成时获得通知。**

