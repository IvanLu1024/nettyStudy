# 第六章 笔记——ChannelHandler 和 ChannelPipeline

## 一、ChannelHandler 家族

### Channel的生命周期

- ChannelUnregistered：Channel 已经被创建，但还未注册到 EventLoop；
- ChannelRegistered：Channel已被注册到EventLoop；
- ChannelActive：Channel 处于活动状态（已经连接到它的远程节点）。它现在可以接收和发送数据了；
- ChannelInactive：Channel没有连接到远程节点。

### ChannelHandler的生命周期

- handlerAdded：当把 ChannelHandler 添加到 ChannelPipeline 中时被调用；
- handlerRemoved：当从 ChannelPipeline 中移除 ChannelHandler 时被调用；
- exceptionCaught：当处理过程中在 ChannelPipeline 中有错误产生时被调用。

### ChannelInboundHandler 接口

处理入站数据以及各种状态变化，下标列出了生命周期方法，这些方法将会在数据被接收或者与其对应的Channel状态发生改变的时候调用。

| 类 型                     | 描 述                                                        |
| ------------------------- | ------------------------------------------------------------ |
| channelRegistered         | 当 Channel 已经注册到它的 EventLoop 并且能够处理 I/O 时被调用 |
| channelUnregistered       | 当 Channel 从它的 EventLoop 注销并且无法处理任何 I/O 时被调用 |
| channelActive             | 当 Channel 处于活动状态时被调用；Channel 已经连接绑定并且已经就绪 |
| channelInactive           | 当 Channel 离开活动状态并且不再连接它的远程节点时被调用      |
| channelReadComplete       | 当 Channel 上的一个读操作完成时被调用                        |
| channelRead               | 当从 Channel 读取数据时被调用                                |
| ChannelWritabilityChanged | 当 Channel 的可写状态发生改变时被调用。                      |
| userEventTriggered        | 当 ChannelnboundHandler.fireUserEventTriggered() 方法被调用时被调用，因为一个 POJO 被传经了 ChannelPipeline |

### ChannelOutboundHandler 接口

处理出站数据并且允许拦截所有的操作，它的一个强大的功能是可以按需推迟操作或者事件，这使得可以通过一些复杂的方法来处理请求。例如，如果到远程节点的写入被暂停了，那么你可以推迟冲刷操作并在稍后继续。

### ChannelHandler 适配器

 ChannelInboundHandlerAdapter 和 ChannelOutboundHandlerAdapter
类，这两个适配器分别提供了ChannelInboundHandler和 ChannelOutboundHandler 的基本实现。通过扩展抽象类 ChannelHandlerAdapter，它们获得了它们共同的超接口ChannelHandler 的方法。

在 ChannelInboundHandlerAdapter 和 ChannelOutboundHandlerAdapter 中所提供的方法体调用了其相关联的 ChannelHandlerContext 上的等效方法，从而将事件转发到了 ChannelPipeline 中的下一个 ChannelHandler 中。

### 资源管理

每当通过调用 ChannelInboundHandler.channelRead()或者 ChannelOutbound-
Handler.write()方法来处理数据时，你都需要确保没有任何的资源泄漏。

为了帮助你诊断潜在的（资源泄漏）问题，Netty提供了class ResourceLeakDetector级别，它将对你应用程序的缓冲区分配做大约1%的采样来检测内存泄露。相关的开销是非常小的。

| 级 别    | 描 述                                                        |
| -------- | ------------------------------------------------------------ |
| DISABLED | 禁用泄漏检测                                                 |
| SIMPLE   | 使用 1%的默认采样率检测并报告任何发现的泄露。这是默认级别，适合绝大部分的情况。 |
| ADVANCED | 使用默认的采样率，报告所发现的任何的泄露以及对应的消息被访问的位置。 |
| PARANOID | 类似于 ADVANCED ，但是其将会对每次（对消息的）访问都进行采样。这对性能将会有很大的影响，应该只在调试阶段使用。 |

泄露检测级别可以通过将下面的 Java 系统属性设置为表中的一个值来定义：

```
java -Dio.netty.leakDetectionLevel=ADVANCED		//将泄漏检测级别设置为ADVANCED
```

- 消费并释放入站消息（简单方式）：

由于消费入站数据是一项常规任务，所以 Netty 提供了一个特殊的被称为 SimpleChannelInboundHandler 的 ChannelInboundHandler 实现。这个实现会在消息被 channelRead0()方法消费之后**自动释放消息**，代码如下：

```java
@Sharable
public class DiscardInboundHandler extends ChannelInboundHandlerAdapter {
@Override
public void channelRead(ChannelHandlerContext ctx, Object msg) {
			ReferenceCountUtil.release(msg);	//调用工具类释放资源
	}
}
```

- 丢弃并释放出站消息：

不仅要释放资源，还要通知 ChannelPromise，否则可能会出现 ChannelFutureListener 收不到某个消息已经被处理了的通知的情况。

```java
@Sharable
public class DiscardOutboundHandler
extends ChannelOutboundHandlerAdapter {
@Override
public void write(ChannelHandlerContext ctx,Object msg, ChannelPromise promise) {
		ReferenceCountUtil.release(msg);	//释放资源
		promise.setSuccess();				//还要通知ChannelPromise
	}
}
```

总之，如果一个消息被消费或者丢弃了，并且没有传递给 ChannelPipeline 中的下一个ChannelOutboundHandler，那么用户就有责任调ReferenceCountUtil.release()。如果消息到达了实际的传输层，那么当它被写入时或者 Channel 关闭时，都将被自动释放。

## 二、ChannelPipeline 接口

ChannelPipeline 是一个拦截流经Channel的入站和出站事件的ChannelHandler的实例链。每一个新创建的 Channel 都将会被分配一个新的 ChannelPipeline， **既不能附加另外一个 ChannelPipeline，也不能分离其当前的。**

根据事件的起源，事件将被分为入站和出站两种操作，随后调用ChannelHandlerContext的实现，它将被转发给同一超类型的下一个ChannelHandler处理。

ChannelHandlerContext：使得ChannelHandler能够和它的ChannelPipeline以及其他的ChannelHandler交互。ChannelHandler可以通知其所属的ChannelPipeline中的下一个ChannelHandler，甚至可以动态修改它所属的ChannelPipeline 

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g26sj1zo0fj20hn04iglr.jpg"/>

如上图所示，ChannelPipeline 的入站位置作为头部，出站位置作为尾部。在 ChannelPipeline 传播事件时，它会测试 ChannelPipeline 中的下一个 Channel-
Handler 的类型是否和事件的运动方向相匹配。如果不匹配，ChannelPipeline 将跳过该ChannelHandler 并前进到下一个，直到它找到和该事件所期望的方向相匹配的为止。

### 修改 ChannelPipeline

ChannelHandler 可以通过调用ChannelPipeline中的添加、删除或者替换其他的 ChannelHandler 来实时地修改ChannelPipeline 的布局。例如：

```java
ChannelPipeline pipeline = ..;
FirstHandler firstHandler = new FirstHandler();
pipeline.addLast("handler1", firstHandler);
pipeline.addFirst("handler2", new SecondHandler());
pipeline.addLast("handler3", new ThirdHandler());
...
pipeline.remove("handler3");	//通过名称移除handler3
pipeline.remove(firstHandler);	
pipeline.replace("handler2", "handler4", new ForthHandler());	//将handler4替换handler2
```

## 三、ChannelHandlerContext 接口

ChannelHandlerContext 代表了 ChannelHandler 和 ChannelPipeline 之间的关
联，**每当有 ChannelHandler 添加到 ChannelPipeline 中时，都会创建 ChannelHandlerContext。**ChannelHandlerContext 的主要功能是管理它所关联的 ChannelHandler 和在同一个 ChannelPipeline 中的其他 ChannelHandler 之间的交互。

ChannelHandlerContext 上有一些和Channel和ChannelPipeline上同样的方法，其中不同在于：

- 如果调用 Channel 或者 ChannelPipeline 上的这些方法，它们将沿着整个 ChannelPipeline 进行传播。
- 调用位于 ChannelHandlerContext上的相同方法，则将从当前所关联的 ChannelHandler 开始，并且只会传播给位于该ChannelPipeline 中的下一个能够处理该事件的ChannelHandler。

使用API的时候，牢记两点：

- ChannelHandlerContext 和 ChannelHandler 之间的关联（绑定）是永远不会改
  变的，所以缓存对它的引用是安全的；
- ChannelHandlerContext的方法将产生更短的事件流，应该尽可能地利用这个特性来获得最大的性能。

### 使用ChannelHandlerContext
调用Channel 或 ChannelPipeline 上的 write()方法将会导致写入事件从尾端到头部地流经ChannelPipeline。

而调用ChannelHandlerContext上的write()方法，消息将从下一个ChannelHandler 开始流经 ChannelPipeline，绕过了所有前面的 ChannelHandler。

为什么会想要从 ChannelPipeline 中的某个特定点开始传播事件呢？

- 为了减少将事件传经对它不感兴趣的 ChannelHandler 所带来的开销；
- 为了避免将事件传经那些可能会对它感兴趣的 ChannelHandler。

### ChannelHandler 和 ChannelHandlerContext 的高级用法

通过调用 ChannelHandlerContext 上的pipeline()方法来获得被封闭的 ChannelPipeline 的引用，利用这一点来实现一些复杂的设计：

- 通过将 ChannelHandler 添加到 ChannelPipeline 中来实现动态的协议切换；
- 缓存到 ChannelHandlerContext 的引用以供稍后使用，这可能会发生在任何的 ChannelHandler 方法之外，甚至来自于不同的线程，代码如下：

```java
public class WriteHandler extends ChannelHandlerAdapter {
    private ChannelHandlerContext ctx;
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
    		this.ctx = ctx;		//存储到ctx的引用以供稍后使用
    }
    public void send(String msg) {
   			ctx.writeAndFlush(msg);		//使用之前存储的到ChannelHandlerContext的引用来发送消息
    }
}
```

因为一个 ChannelHandler 可以从属于多个 ChannelPipeline，所以它也可以绑定到多
个 ChannelHandlerContext 实例。对于这种用法指在多个 ChannelPipeline 中共享同一个 ChannelHandler ，对应的 ChannelHandler 必须要使用@Sharable 注解标注；否则，试图将它添加到多个 ChannelPipeline 时将会触发异常。**显而易见，为了安全地被用于多个并发的 Channel（即连接），这样的 ChannelHandler 必须是线程安全的。**

在多个ChannelPipeline中安装同一个ChannelHandler的一个常见的原因是**用于收集跨越多个Channel 的统计信息。**

## 四、异常处理

Netty中提供了几种方式用于处理入站和出站处理过程中抛出的异常。

### 处理入站异常

要想处理这种类型的入站异常，你需要在你的 ChannelInboundHandler 实现中重写exceptionCaught()这个函数。

因为异常将会继续按照入站方向流动，所以异常处理通常位于ChannelPipeline的最后，这确保了所有的入站异常都总是会被处理，无论它们可能发生在ChannelPipeline的什么位置。

- ChannelHandler.exceptionCaught()的默认实现是简单地将当前异常转发给
  ChannelPipeline 中的下一个 ChannelHandler；
- 如果异常到达了 ChannelPipeline 的尾端，它将会被记录为未被处理；
- 要想定义自定义的处理逻辑，你需要重写 exceptionCaught()方法。然后你需要决定是否需要将该异常传播出去

### 处理出站异常

用于处理出站操作中的正常完成以及异常的选项，都基于以下的通知机制：

- 每个出站操作都将返回一个ChannelFuture，ChannelFutureListener 将在操作完成时被通知该操作是成功了还是出错了；
- 几乎所有的 ChannelOutboundHandler 上的方法都会传入一个 ChannelPromise
  的实例，具有异步通知和立即通知两种方式。

添加 ChannelFutureListener 只需要调用 ChannelFuture 实例上的 addListener
(ChannelFutureListener)方法，其中有两种实现方式：

- 添加 ChannelFutureListener 到 ChannelFuture：

```java
	ChannelFuture future = channel.write(someMessage);
    future.addListener(new ChannelFutureListener() {
    @Override
    public void operationComplete(ChannelFuture f) {
            if (!f.isSuccess()) {
            f.cause().printStackTrace();
            f.channel().close();
    	}
    }
});
```

- 添加 ChannelFutureListener 到 ChannelPromise：

```java
public class OutboundExceptionHandler extends ChannelOutboundHandlerAdapter {
    @Override
    public void write(ChannelHandlerContext ctx, Object msg,
    ChannelPromise promise) {
    	promise.addListener(new ChannelFutureListener() {
    	@Override
    	public void operationComplete(ChannelFuture f) {
        if (!f.isSuccess()) {
        f.cause().printStackTrace();
        f.channel().close();
    		}
    	}
    });
    }
}
```

