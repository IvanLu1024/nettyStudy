# 第五章 笔记——ByteBuf

ByteBuf作为Netty中字节容器，既解决了 JDK API 的局限性，又为网络应用程序的开发者提供了更好的 API。其优点有：

- 它可以被用户自定的缓冲区类型扩展；
- 通过内置的复合缓冲区类型实现了透明的零拷贝；
- 容量可以按需增长（类似于 JDK 的 StringBuilder）；
- 在读和写这两种模式之间切换不需要调用 ByteBuffer 的 flip()方法；
- 读和写使用了不同的索引；
- 支持方法的链式调用；
- 支持引用计数；
- 支持池化。

## ByteBuf类

作为Netty的数据容器，其实现具有高效性和易用性。

它维护了两个不同的索引：

- readerIndex：读索引
- writerIndex：写索引

readerIndex和writerIndex是初始都是指向索引为0的位置，当读取的时候，发现两者值相等则表示此时容量为0，每个读取操作会使得readerIndex加一，同样写操作也会使得writerIndex加一。

### 使用模式

- 堆缓冲区

最常用的 ByteBuf 模式是将数据存储在 JVM 的堆空间中。这种模式被称为支撑数组（backing array），它能在没有使用池化的情况下提供快速的分配和释放。

- 直接缓冲区

**为了避免在每次调用本地 I/O 操作之前（或者之后）将缓冲区的内容复制到一个中间缓冲区（或者从中间缓冲区把内容复制到缓冲区）**，会使用直接缓冲区。其缺点是相对于基于堆的缓冲区，它们的分配和释放都较为昂贵。

- 复合缓冲区

它为多个 ByteBuf 提供一个聚合视图，在这里你可以根据需要添加或者删除 ByteBuf 实例。Netty 通过一个 ByteBuf 子类——CompositeByteBuf ——实现了这个模式，它提供了一个将多个缓冲区表示为单个合并缓冲区的虚拟表示。

## 字节级操作

### 随机访问索引

使用那些需要一个索引值参数的方法（的其中）之一来访问数据既不会改变readerIndex 也不会改变writerIndex

### 顺序访问索引

ByteBuf内部可以被两个索引划分成3个区域：

- 已经被读过的可以被丢弃的字节；
- 尚未被读过的可以被读取的字节；
- 可写字节。

### 可丢弃字节

调用discardReadBytes()方法后的结果是可丢弃字节分段中的空间已经变为可写的了。

虽然你可能会倾向于频繁地调用 discardReadBytes()方法以确保可写分段的最大化，但是请注意，**这将极有可能会导致内存复制**，因为可读字节（图中标记为 CONTENT 的部分）必须被移动到缓冲区的开始位置。

### 索引管理

可以通过调用markReaderIndex()、markWriterIndex()、resetWriterIndex()和 resetReaderIndex()来标记和重置 ByteBuf 的 readerIndex 和 writerIndex。也可以通过调用readerIndex(int)或者 writerIndex(int)来将索引移动到指定位置。可以通过调用 clear()方法来将 readerIndex 和 writerIndex 都设置为 0。注意，这并不会清除内存中的内容。调用 clear()比调用 discardReadBytes()轻量得多，**因为它将只是重置索引而不会复制任何的内存。**

## ByteBufHolder 接口

为了存储除了字节内容以外的属性值，Netty提供了ByteBufHolder，ByteBufHolder还有一些高级特性，如缓冲区池化，其中可以从池中借用 ByteBuf，并且在需要时自动释放。

## ByteBuf 分配

### 按需分配：ByteBufAllocator 接口

为了降低分配和释放内存的开销，Netty 通过 interface ByteBufAllocator 实现了（ByteBuf 的）池化，它可以用来分配我们所描述过的任意类型的 ByteBuf 实例。

Netty提供了两种ByteBufAllocator的实现：PooledByteBufAllocator和Unpooled-
ByteBufAllocator。前者池化了ByteBuf的实例以提高性能并最大限度地减少内存碎片。此实

现使用了一种称为jemalloc的已被大量现代操作系统所采用的高效方法来分配内存。后者的实现是不使用池化ByteBuf实例，并且在每次它被调用的时候返回一个新的实例。

### Unpooled 缓冲区

可能某些情况下，你未能获取一个到 ByteBufAllocator 的引用。对于这种情况，Netty 提供了一个简单的称为 Unpooled 的工具类，**它提供了静态的辅助方法来创建未池化的 ByteBuf实例。**

### ByteBufUtil类

ByteBufUtil 提供了用于操作 ByteBuf 的静态的辅助方法。这些静态方法中最有价值的可能就是 hexdump()方法，它以十六进制的表示形式打印ByteBuf 的内容。这在各种情况下都很有用，例如，出于调试的目的记录 ByteBuf 的内容。十六进制的表示通常会提供一个比字节值的直接表示形式更加有用的日志条目，此外，十六进制的版本还可以很容易地转换回实际的字节表示。另一个有用的方法是 boolean equals(ByteBuf, ByteBuf)，它被用来判断两个 ByteBuf
实例的相等性。

## 引用计数

引用计数是一种通过在某个对象所持有的资源不再被其他对象引用时释放该对象所持有的资源来优化内存使用和性能的技术。

引用计数背后的想法并不是特别的复杂；它主要涉及跟踪到某个特定对象的活动引用的数
量。一个 ReferenceCounted 实现的实例将通常以活动的引用计数为 1 作为开始。只要引用计数大于 0，就能保证对象不会被释放。当活动引用的数量减少到 0 时，该实例就会被释放。

这和JVM中垃圾回收中的引用计数是类似的。注意，虽然释放的确切语义可能是特定于实现的，但是至少已经释放的对象应该不可再用了。
引用计数对于池化实现（如 PooledByteBufAllocator ）来说是至关重要的，**它降低了**
**内存分配的开销。**