package chapter9.Inbound;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import static org.junit.Assert.*;

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
