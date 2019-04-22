package chapter9.exception;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import org.junit.Assert;
import org.junit.Test;
import static org.junit.Assert.*;

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
