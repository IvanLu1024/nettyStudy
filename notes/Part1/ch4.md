# 第四章 笔记——传输

## 传输API

传输API的核心是interface Channel，它被用于所有的 I/O 操作。Channel类的层次结构如图所示：

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g25mdd9tm6j20lt06qgm6.jpg"/>

每个 Channel 都将会被分配一个 ChannelPipeline 和 ChannelConfig。ChannelConfig 包含了该 Channel 的所有配置设置，并且支持热更新。**为了保证Channel 的唯一性**，所以继承了Comparable接口，当两个不同的Channel实例都返回了相同的HashCode，则会抛出一个Error。

ChannelPipeline 持有**所有**将应用于入站和出站数据以及事件的 ChannelHandler 实例，这些 ChannelHandler 实现了应用程序用于处理状态变化以及数据处理的逻辑。除了访问所分配的 ChannelPipeline 和 ChannelConfig 之外，Channel的其他重要方法如下：

| 方法          | 描述                                                         |
| ------------- | ------------------------------------------------------------ |
| eventLoop     | 返回分配给 Channel 的 EventLoop                              |
| pipeline      | 返回分配给 Channel 的 ChannelPipeline                        |
| isActive      | 如果 Channel 是活动的，则返回 true 。活动的意义可能依赖于底层的传输。例如，一个 Socket 传输一旦连接到了远程节点便是活动的，而一个 Datagram 传输一旦被打开便是活动的 |
| localAddress  | 返回本地的 SokcetAddress                                     |
| remoteAddress | 返回远程的 SocketAddress                                     |
| write         | 将数据写到远程节点。这个数据将被传递给 ChannelPipeline ，并且排队直到它被冲刷 |
| flush         | 将之前已写的数据冲刷到底层传输，如一个 Socket                |
| writeAndFlush | 一个简便的方法，等同于调用 write() 并接着调用 flush()        |

Channel的底层实现是**线程安全**的。

## 内置传输

Netty提供以下传输：

| 名称     | 包                          | 描述                                                         |
| -------- | --------------------------- | ------------------------------------------------------------ |
| NIO      | io.netty.channel.socket.nio | 使用 java.nio.channels 包作为基础——基于选择器（selector）的方式 |
| Epoll    | io.netty.channel.epoll      | 由 JNI 驱动的 epoll() 和非阻塞 IO。这个传输支持只有在Linux上可用的多种特性，如 SO_REUSEPORT ，比NIO 传输更快，而且是完全非阻塞的 |
| OIO      | io.netty.channel.socket.oio | 使用 java.net 包作为基础——使用阻塞流                         |
| Local    | io.netty.channel.local      | 可以在 VM 内部通过管道进行通信的本地传输                     |
| Embedded | io.netty.channel.embedded   | Embedded 传输，允许使用 ChannelHandler 而又不需要一个真正的基于网络的传输。这在测试你的
ChannelHandler 实现时非常有用 |

### NIO

是一种非阻塞IO，基于选择器的机制。选择器充当一个注册表，当Channel状态发生变化的时候会得到通知，可能的状态变化：

- 新的 Channel 已被接受并且就绪；
- Channel 连接已经完成；
- Channel 有已经就绪的可供读取的数据；
- Channel 可用于写数据。

选择器由一个线程专门负责检查状态变化并对其做出相应响应。

其处理流程如下图所示：

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g25ol8u60xj20m70czjts.jpg"/>

1. 新的Channel注册到选择器；
2. 选择器处理状态变化的通知；
   1. 若有状态改变，则线程将执行该任务；
   2. 若没有改变，线程则继续执行其他任务

### Epoll

用于Linux的本地非阻塞传输，Netty为Linux提供了一组NIO API，其以一种和它本身的设计更加一致的方式使用epoll，并且以一种更加轻量的方式使用中断。

### OIO

Netty是如何能够使用和用于异步传输相同的API来支持OIO的呢？

答案就是，Netty利用了SO_TIMEOUT这个Socket标志，它指定了等待一个I/O操作完成的最大毫秒数。如果操作在指定的时间间隔内没有完成，则将会抛出一个SocketTimeout Exception。Netty将捕获这个异常并继续处理循环。在EventLoop下一次运行时，它将再次尝试。

### Local

这种传输方式，是用于**同一个JVM中**运行的客户端和服务器之间的异步通信。

在这个传输中，和服务器 Channel 相关联的 SocketAddress 并**没有绑定物理网络地址**；相反，只要服务器还在运行，它就会被存储在注册表里，并在 Channel 关闭时注销。因为这个传输并不接受真正的网络流量，所以它并不能够和其他传输实现进行互操作。因此，客户端希望连接到（在同一个 JVM 中）使用了这个传输的服务器端时也必须使用它。除了这个限制，它的使用方式和其他的传输一模一样。

### Embedded

Netty 提供了一种额外的传输，使得你可以将一组 ChannelHandler 作为帮助器类嵌入到其他的 ChannelHandler 内部。通过这种方式，你将可以扩展一个 ChannelHandler 的功能，而又不需要修改其内部代码。