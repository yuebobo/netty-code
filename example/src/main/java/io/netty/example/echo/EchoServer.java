/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.echo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.FixedLengthFrameDecoder;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.concurrent.DefaultEventExecutorChooserFactory;

import java.net.SocketAddress;
import java.nio.channels.Selector;

/**
 * Echoes back any received data from a client.
 */
public final class EchoServer {

    static final boolean SSL = System.getProperty("ssl") != null;
    static final int PORT = Integer.parseInt(System.getProperty("port", "8007"));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        } else {
            sslCtx = null;
        }

        // Configure the server.


        /**
         * 新建一个NioEventLoopGroup对象 取名 bossGroup
         * 1.EventExecutor[] children  数组里存的是 {@link io.netty.channel.nio.NioEventLoop} 的数组
         *  1.1 NioEventLoop 的属性
         *   1.1.1 EventExecutorGroup parent ：存的是 就是当前的 NioEventLoopGroup对象 bossGroup
         *   1.1.2 SelectorProvider provider  保存的是 SelectorProvider
         *                      可以创建 selector {@link Selector#open()} 和 ServerSocketChannel   {@link java.nio.channels.ServerSocketChannel#open()}
         *   1.1.3 Selector unwrappedSelector  由上一步的 provider.openSelector() 创建    是 {@link sun.nio.ch.WindowsSelectorImpl}
         *              属性值{@link sun.nio.ch.WindowsSelectorImpl#selectedKeys}   通过反射  由  HashSet 替换成 {@link io.netty.channel.nio.SelectedSelectionKeySet }
         *              属性值 {@link sun.nio.ch.WindowsSelectorImpl#publicSelectedKeys}  通过反射 由 匿名的Set类 替换成 {@link io.netty.channel.nio.SelectedSelectionKeySet }
         *   1.1.4 Selector selector 一个包装了上一步 unwrappedSelector 对象的对象  SelectedSelectionKeySetSelector
         *   1.1.5 SelectedSelectionKeySet selectedKeys  {@link io.netty.channel.nio.SelectedSelectionKeySet }
         *   1.1.6 SelectStrategy selectStrategy  选择策略 在启动时 创建了新的线程后，新的线程工作内容的选择策略
         *
         * 2.EventExecutorChooserFactory.EventExecutorChooser chooser 保存一个选择器(里边包含着children)
         *      2.1 如果children数组的长度是2的幂次方 则 chooser 为 {@link io.netty.util.concurrent.DefaultEventExecutorChooserFactory.PowerOfTwoEventExecutorChooser}
         *          否则 为 {@link io.netty.util.concurrent.DefaultEventExecutorChooserFactory.GenericEventExecutorChooser}
         *      2.2 后边调用 bossGroup.register(X xx) 时 会调用 chooser 的next() 方法返回 children 数组里的一个 NioEventLoop 然后执行里边的 register(X xx)方法
         */
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();


            /**
             * 将 bossGroup 赋值给 group
             * 将 workerGroup 赋值给  childGroup
             */
            b.group(bossGroup, workerGroup)


                    /**
                     * 将 new ReflectiveChannelFactory（） 对象 赋值给 channelFactory
                     * channelFactory 有一个 newChannel() 方法 通过反射 创建一个 NioServerSocketChannel 类型的对象
                     *  反射创建的时候注意调用了构造方法
                     */
                    .channel(NioServerSocketChannel.class)



                    .option(ChannelOption.SO_BACKLOG, 100)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            if (sslCtx != null) {
                                p.addLast(sslCtx.newHandler(ch.alloc()));
                            }
                            //p.addLast(new LoggingHandler(LogLevel.INFO));
//                            p.addLast(new EchoServerHandler());
//                            p.addLast(new FixedLengthFrameDecoder(5));
//                            p.addLast(new LineBasedFrameDecoder(Integer.MAX_VALUE));
                            p.addLast(new DelimiterBasedFrameDecoder(Integer.MAX_VALUE, Unpooled.wrappedBuffer(new byte[]{'#','*'})));
                        }
                    });

            // Start the server.

            /**
             * 启动服务
             *  主要方法
             *  {@link ServerBootstrap#doBind(SocketAddress)}
             *      {@link ServerBootstrap#initAndRegister()}
             *          {@link ServerBootstrap#init(Channel)}
             *      {@link ServerBootstrap#doBind0(ChannelFuture, Channel, SocketAddress, ChannelPromise)}
             *
             *
             * 一.initAndRegister（）初始化与注册
             *   1.调用 NioServerSocketChannel 的构造方法 创建对象  赋值给 channel 注意构造方法的执行过程
             *   2.调用 init（channel）方法  通过上一步活动的 channel 获得 pipline ，往pipline 里 添加一个元素 （new ChannelInitializer（） 匿名内部类）
             *   3.调用 config().group().register(channel)
             *                      == 》  NioEventLoopGroup 的 register（channel）
             *                      == 》  NioEventLoop      的 register（channel)
             *                      == 》  channel.unsafe  （NioMessageUnsafe）  的 register（nioEventLoop ，promise）  返回 Promise 对象
             *                               把unsafe 里的 register0（promise） 打包成一个任务，添加的任务队列里，
             *                               启动线程 ，调用 NioEventLoop 的 run（） 方法
             *                                      run（） 方法为一个死循环
             *                                          1.selector.select（（time）） 监听新的事件
             *                                          2.处理监听到的事件
             *                                          3.处理队列中的任务
             *
             *
             */
            ChannelFuture f = b.bind(PORT).sync();

            // Wait until the server socket is closed.
            f.channel().closeFuture().sync();
        } finally {
            // Shut down all event loops to terminate all threads.
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
