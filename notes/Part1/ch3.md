# 第三章 笔记——Netty 的组件和设计

Netty解决了下面两个领域的问题：

- 技术：异步和事件驱动实现，保证高负载下的性能最大化和可伸缩性；
- 体系结构：简化开发过程，最大程度上提高了可测试性、模块化以及代码的可重用性。

## Channel、EventLoop 和 ChannelFuture

这三个类是Netty网络抽象的代表：

- Channel：Socket；
- EventLoop：控制流、多线程处理、并发；
- ChannelFuture：异步通知。

### Channel接口

一个接口，是与网络套接字（Socket）或能够执行I / O操作（如读取，写入，连接和绑定）的组件的连接，**并且所有I/O操作都是异步的**，封装了Socket，降低了使用的复杂性。

### EventLoop 接口

用于处理连接的生命周期中所发生的时间，如图所示：

- 一个 EventLoopGroup 包含一个或者多个 EventLoop；
- 一个 EventLoop 在它的生命周期内**只和一个 Thread 绑定**；
- 所有由 EventLoop 处理的 I/O 事件都将在它专有的 Thread 上被处理；
- 一个 Channel 在它的生命周期内**只注册于一个 EventLoop**；
- 一个 EventLoop 可能会被分配给一个或多个 Channel。

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g25ednn3zmj20fk0agab6.jpg"/>

### ChannelFuture 

可以将 ChannelFuture 看作是**将来要执行的操作的结果的占位符。**它究竟 什么时候 被执行则可能取决于若干的因素，因此不可能准确地预测，但是可以肯定的是它将会被执行。此外，所有属于同一个 Channel 的操作都被保证其将以它们被调用的顺序被执行。

## ChannelHandler 和 ChannelPipeline

### ChannelHandler

在开发人员的角度出发，Netty的主要组件是ChannelHandler ，它充当了所有处理入站和出站数据的应用程序逻辑的**容器**。

Netty 以**适配器**类的形式提供了大量默认的 ChannelHandler 实现，其旨在简化应用程序处理逻辑的开发过程。这些适配器类（及它们的子类）将自动执行将负责把事件转发到链中的下一个 ChannelHandler这个操作，所以你可以只重写那些你想要特殊处理的方法和事件。

### ChannelPipeline

提供了ChannelHandler链的容器，定义了用于该链上传播入站和出站事件流的API。ChannelHandler 安装到 ChannelPipeline 中的过程如下所示：

- 一个ChannelInitializer的实现被注册到了ServerBootstrap中；
- 当 ChannelInitializer.initChannel()方法被调用时，ChannelInitializer将在 ChannelPipeline 中安装一组自定义的 ChannelHandler；
- ChannelInitializer 将它自己从 ChannelPipeline 中移除；

ChannelPipeline接收事件、执行事件逻辑，并将数据传递给链中的下一个ChannelHandler，其执行顺序由添加顺序所决定。

从客户端的角度看，事件的运动方向出站表示从客户端到服务器，反之称为入站。

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g25f6kz0i9j20k305awep.jpg"/>

如图所示，入站和出站ChannelHandler可以放在同一个ChannelPipeline中，入站事件从头部开始，出站事件从尾部开始。

在Netty中，有两种发送消息的方式。你可以直接写到Channel中，也可以 写到和Channel-
Handler相关联的ChannelHandlerContext对象中。前一种方式将会导致消息从Channel-
Pipeline 的尾端开始流动，而后者将导致消息从 ChannelPipeline 中的下一个 Channel-
Handler 开始流动。

## 引导

Netty 的引导类为应用程序的网络层配置提供了容器，这涉及将一个进程绑定到某个指定的端口，或者将一个进程连接到另一个运行在某个指定主机的指定端口上的进程。前者表示引导一个服务器，后者表示引导一个客户端。

服务器和客户端引导类的区别还有一个区别是EventLoopGroup的数目，服务器引导类需要2个EventLoopGroup，而客户端引导类只需要1个。

  因为服务器需要两组不同的Channel：

- 第一组将只包含一个 ServerChannel，代表服务器自身的已绑定到某个本地端口的正在监听的套接字；
- 而第二组将包含所有已创建的用来处理传入客户端连接（对于每个服务器已经接受的连接都有一个）的 Channel。

下图表示服务器为何需要两个EventLoopGroup

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g25fyejguxj20ke09l760.jpg"/>

