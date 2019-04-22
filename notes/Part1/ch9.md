# 第 九 章 笔记——单元测试 

Netty 提供了它所谓的 Embedded 传输，用于测试 ChannelHandler。这个传输是一种特殊的Channel 实现 — EmbeddedChannel — 的功能，这个实现提供了通过 ChannelPipeline传播事件的简便方法。

这个想法是直截了当的：将入站数据或者出站数据写入到 EmbeddedChannel 中，然后检查是否有任何东西到达了 ChannelPipeline 的尾端。以这种方式，你便可以确定消息是否已经被编码或者被解码过了，以及是否触发了任何的 ChannelHandler 动作。

下图展示了使用 EmbeddedChannel 的方法，**数据是如何流经 ChannelPipeline 的。你可以使用 writeOutbound()方法将消息写到 Channel 中，并通过 ChannelPipeline 沿着出站的方向传递。随后，你可以使用 readOutbound()方法来读取已被处理过的消息，以确定结果是否和预期一样。** 类似地，对于入站数据，你需要使用writeInbound()和readInbound()方法。

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g2ajwv514tj20kp087dgb.jpg"/>

## 一、使用 EmbeddedChannel 测试 ChannelHandler

### 测试入站消息

下图展示了一个简单的 ByteToMessageDecoder 实现。给定足够的数据，这个实现将
产生固定大小的帧。如果没有足够的数据可供读取，它将等待下一个数据块的到来，并将再次检
查是否能够产生一个新的帧。

![1555853720783](C:\Users\Wei Yu\AppData\Roaming\Typora\typora-user-images\1555853720783.png)

代码如下所示：

```java
public class FixedLengthFrameDecoder extends ByteToMessageDecoder {

    private final int frameLength;

    public FixedLengthFrameDecoder(int frameLength) {
        if (frameLength<0){
            throw new IllegalArgumentException("frameLength must be a positive integer:"+frameLength);
        }
        this.frameLength = frameLength;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        while (in.readableBytes()>=frameLength){     //检查是否有足够的字节可以被读取，以生成下一个帧
            ByteBuf buf = in.readBytes(frameLength);    //从输入的ByteBuf读取一个固定大小为frameLength的帧
            out.add(buf);       //将该帧添加到已被解码的消息列表中
        }
    }
}
```

```java
public class FixedLengthFrameDecoderTest {

    @Test
    public  void testFrame(){
        ByteBuf buf = Unpooled.buffer();
        for (int i = 0; i < 9; i++) {
            buf.writeByte(i);
        }
        ByteBuf input = buf.duplicate();
        EmbeddedChannel channel = new EmbeddedChannel(new FixedLengthFrameDecoder(3));
        //写入字节
        assertTrue(channel.writeInbound(input.retain()));
        assertTrue(channel.finish());

        //读取消息
        ByteBuf read = (ByteBuf) channel.readInbound();
        assertEquals(buf.readSlice(3),read);
        read.release();

        read=(ByteBuf) channel.readInbound();
        assertEquals(buf.readSlice(3),read);
        read.release();

        read=(ByteBuf) channel.readInbound();
        assertEquals(buf.readSlice(3),read);
        read.release();

        assertNull(channel.readInbound());
        buf.release();

    }

    @Test
    public void testFrame2(){
        ByteBuf buf = Unpooled.buffer();
        for (int i = 0; i < 9; i++) {
            buf.writeByte(i);
        }
        ByteBuf input = buf.duplicate();
        EmbeddedChannel channel = new EmbeddedChannel(new FixedLengthFrameDecoder(3));


        assertFalse(channel.writeInbound(input.readBytes(2)));  //由于没有一个完整可供读取的帧，则返回false
        assertTrue(channel.writeInbound(input.readBytes(7)));

        assertTrue(channel.finish());
        ByteBuf read=(ByteBuf)channel.readInbound();
        assertEquals(buf.readSlice(3),read);
        read.release();

        read=(ByteBuf) channel.readInbound();
        assertEquals(buf.readSlice(3),read);
        read.release();

        read=(ByteBuf) channel.readInbound();
        assertEquals(buf.readSlice(3),read);
        read.release();

        assertNull(channel.readInbound());
        buf.release();

    }

}
```

### 测试出站消息

现在我们只需要简单地提及我们正在测试的处理器 — AbsIntegerEncoder，它是 Netty 的MessageToMessageEncoder 的一个特殊化的实现，用于将负值整数转换为绝对值：

- 持有 AbsIntegerEncoder 的 EmbeddedChannel 将会以 4 字节的负整数的形式写出
  站数据；

- 编码器将从传入的 ByteBuf 中读取每个负整数，并将会调用 Math.abs()方法来获取
  其绝对值；
- 编码器将会把每个负整数的绝对值写到 ChannelPipeline 中。

![1555855775008](C:\Users\Wei Yu\AppData\Roaming\Typora\typora-user-images\1555855775008.png)

代码如下：

```java
public class AbsIntegerEncoder extends MessageToMessageEncoder<ByteBuf> {



    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        while (in.readableBytes()>=4){
            int value = Math.abs(in.readInt());     //从输入的ByteBuf中读取下一个整数，并计算其绝对值
            out.add(value);     //将该整数的绝对值写入编码消息的List中
        }

    }
}
```

```java
public class AbsIntegerEncoderTest {
    @Test
    public void testEncoder(){
        ByteBuf buf = Unpooled.buffer();
        for (int i = 1; i < 10; i++) {
            buf.writeInt(i*-1);
        }

        EmbeddedChannel channel = new EmbeddedChannel(new AbsIntegerEncoder());
        assertTrue(channel.writeOutbound(buf));
        assertTrue(channel.finish());

        for (int i = 1; i < 10; i++) {
            assertEquals(i,channel.readOutbound());
        }

        assertNull(channel.readOutbound());

    }
}
```

### 测试异常处理

将最大的帧大小已经被设置为3 字节，如果一个帧的大小超出了该限制，那么程序将会丢弃它的字节，并抛出一个TooLong-FrameException。位于 ChannelPipeline 中的其他ChannelHandler 可以选择在exceptionCaught()方法中处理该异常或者忽略它。如下图所示：

<img src="https://ws1.sinaimg.cn/mw690/b7cbe24fly1g2b82b3mcmj20h201mglj.jpg"/>

代码如下：

```java
public class FrameChunkDecoder extends ByteToMessageDecoder {
    private final int maxFrameLength;

    public FrameChunkDecoder(int maxFrameLength) {
        this.maxFrameLength = maxFrameLength;
    }

    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int readableBytes = in.readableBytes();
        if (readableBytes>maxFrameLength){
            //丢弃字节
            in.clear();
            throw new TooLongFrameException();
        }
        //从输入的ByteBuf读取一个新的帧
        ByteBuf buf = in.readBytes(readableBytes);
        //将这个帧封装到解码消息的List中
        out.add(buf);
    }
}
```

```java
public class FrameChunkDecoderTest {
    @Test
    public void framesDecoderTest(){
        ByteBuf buf = Unpooled.buffer();
        for (int i = 0; i < 9; i++) {
            buf.writeByte(i);
        }
        ByteBuf input = buf.duplicate();
        EmbeddedChannel channel = new EmbeddedChannel(new FrameChunkDecoder(3));
        assertTrue(channel.writeInbound(input.readBytes(2)));
        try{
            channel.writeInbound(input.readBytes(4));
            //如果没有抛出异常，表示本次测试失败
            Assert.fail();
        }catch (TooLongFrameException e){
            System.out.println("TooLongFrameException occur ---- ");
        }

        assertTrue(channel.writeInbound(input.readBytes(3)));
        assertTrue(channel.finish());

        ByteBuf read=(ByteBuf)channel.readInbound();
        assertEquals(buf.readSlice(2),read);
        read.release();

        read=channel.readInbound();
        assertEquals(buf.skipBytes(4).readSlice(3),read);
        read.release();
        buf.release();

    }
}
```