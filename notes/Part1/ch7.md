# 第 7 章 笔记—— EventLoop 和线程模型

线程模型指定了操作系统、编程语言、框架或者应用程序的上下文中的线程管理
的关键方面。

## 一、线程模型概述

Java5以后引入了Executor API，其线程池通过缓存和重用Thread极大提高了性能，基本的线程池化模型可以描述为：

- 从池的空闲线程列表中选择一个 Thread，并且指派它去运行一个已提交的任务（一个Runnable 的实现）；
- 当任务完成时，将该Thread返回给该列表，使其可被重用。

其执行逻辑如图所示：

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g271gd6qdyj20ll09ydhm.jpg"/>

虽然池化和重用线程相对于简单地为每个任务都创建和销毁线程是一种进步，但是它并不能消除由上下文切换所带来的开销，其将随着线程数量的增加很快变得明显，并且在高负载下愈演愈烈。此外，仅仅由于应用程序的整体复杂性或者并发需求，在项目的生命周期内也可能会出现其他和线程相关的问题。

## 二、EventLoop 接口

Netty中使用“事件循环”这个概念来处理连接的生命周期内发生的所有事件。

Netty 的 EventLoop 是协同设计的一部分，它采用了两个基本的 API：并发和网络编程。

EventLoop的类层次结构如下所示：

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g271wzsa92j20ku0il401.jpg"/>

在这个模型中，一个 EventLoop 将由一个**永远都不会改变**的 Thread 驱动，同时任务（Runnable 或者 Callable）可以直接提交给 EventLoop 实现，以立即执行或者调度执行。

事件/任务的执行顺序为FIFO，因为这样可以通过保证字节内容总是按正确的顺序被处理，消除了潜在的数据损坏的可能性。

### Netty 4 中的 I/O 和事件处理

所有的I/O操作和事件都由已经被分配给了EventLoop的同一个Thread来处理。

### Netty 3 中的 I/O 操作

线程模型只保证了入站（之前称为上游）事件会在所谓的 I/O 线程（对应于 Netty 4 中的 EventLoop）中执行。所有的出站（下游）事件都由调用线程处理，其可能是 I/O 线程也可能是别的线程。

当出站事件触发了入站事件时，将会导致另一个负面影响。当 Channel.write()方法导
致异常时，需要生成并触发一个 exceptionCaught 事件。由于这是一个入站事件，需要在调用线程中执行代码，然后将事件移交给 I/O 线程去执行，然而这将带来额外的上下文切换。

## 三、任务调度

### JDK的任务调度API

使用ScheduledExecutorService调度，在高负载下它将带来性能上的负担。

```java
ScheduledExecutorService executor =
Executors.newScheduledThreadPool(10);
ScheduledFuture<?> future = executor.schedule(
new Runnable() {
@Override
public void run() {
System.out.println("60 seconds later");
}
}, 60, TimeUnit.SECONDS);
...
executor.shutdown();
```

### 使用 EventLoop 调度任务

ScheduledExecutorService 的实现具有局限性，例如，事实上作为线程池管理的一部
分，将会有额外的线程创建。如果有大量任务被紧凑地调度，那么这将成为一个瓶颈。Netty 通过 Channel 的 EventLoop 实现任务调度解决了这一问题，Netty的EventLoop扩展了ScheduledExecutorService。

## 四、实现细节

### 线程管理

Netty线程模型的卓越性能取决于对于当前执行的Thread的身份的确定，确定它是否分配给当前Channel以及它的EventLoop的那一个线程。

如果调用线程正是支撑EventLoop的那个线程，那么所提交的代码块将会被直接执行；否则将调度该任务以便稍后执行，并将其放入内部队列中。当 EventLoop下次处理它的事件时，它会执行队列中的那些任务/事件。其中，每个EventLoop都有它自己独立的任务队列。执行逻辑如下图所示：

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g273606oc5j20n509jq4q.jpg"/>

### EventLoop/线程的分配

根据不同的传输实现，EventLoop的创建和分配方式也不同：

1. 异步传输

只使用少量的EventLoop（每个EventLoop关联一个线程），来支持大量的Channel，用于NIO或AIO这样的非阻塞传输。

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g273gqm2o6j20ka0baq5d.jpg"/>

EventLoopGroup 负责为每个新创建的 Channel 分配一个 EventLoop。在当前实现中，使用顺序循环（round-robin）的方式进行分配以获取一个均衡的分布，并且相同的 EventLoop可能会被分配给多个 Channel。

**一旦一个 Channel 被分配给一个 EventLoop，它将在它的整个生命周期中都使用这个EventLoop（以及相关联的 Thread）。**

2. 阻塞传输

每一个 Channel 都将被分配给一个 EventLoop（以及它的 Thread），如下图所示：

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g273rxb1agj20jc081t9y.jpg"/>

但是，正如同之前一样，得到的保证是每个 Channel 的 I/O 事件都将只会被一个 Thread（用于支撑该 Channel 的 EventLoop 的那个 Thread）处理。这也是另一个 Netty 设计一致性的例子，它（这种设计上的一致性）对 Netty 的可靠性和易用性做出了巨大贡献。

