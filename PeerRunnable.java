import java.util.*;
import java.util.concurrent.TimeUnit;

public abstract class PeerRunnable implements Runnable {
	protected Peer peer;

	public static class StartAndReadRunnable extends PeerRunnable {
		public StartAndReadRunnable(Peer peerToManage) {
			peer = peerToManage;
		}

		public void run() {
			peer.start();
		}

		public void peerDidHandshake(Boolean peerIsLegit) {
			if (peerIsLegit == true) {
				WriteRunnable newWriteRunnable = new WriteRunnable(peer);
				peer.writeThread = newWriteRunnable;
				(new Thread(newWriteRunnable)).start();
				peer.startReading();
			}
		}
	}

	public static class HS_StartAndReadRunnable extends StartAndReadRunnable {
		protected Handshake hs;

		public HS_StartAndReadRunnable(Peer peerToManage, Handshake peer_hs) {
			super(peerToManage);
			peer.readThread = this;
			hs = peer_hs;
		}

		@Override
		public void run(){
			peer.start(hs);
		}
	}

	public static class WriteRunnable extends PeerRunnable {
		private Boolean running;
		private Queue<MessageData> writeQueue;

		protected WriteRunnable(Peer peerToManage) {
			peer = peerToManage;
			running = false;
			writeQueue = new ArrayDeque<>();
		}

		public void run() {
			running = true;
			try {
				while (running == true) {
					PeerEvent<? extends EventPayload> event = peer.eventQueue.poll(90, TimeUnit.SECONDS);
					if (event != null) {
						if (event.type == PeerEvent.Type.MESSAGE_TO_SEND && event.payload instanceof MessageData) {
							MessageData message = (MessageData)event.payload;
							if (message.type == Message.REQUEST)
								writeQueue.add(message);
							else
								peer.writeToSocket(message);
						}
						else if (event.type == PeerEvent.Type.SHUTDOWN)
							running = false;
					} else {
						peer.writeToSocket(new MessageData(Message.KEEPALIVE));
					}

					if (peer.getIsChocking() == false) {
						for (MessageData msg = writeQueue.poll(); msg != null; msg = writeQueue.poll()) {
							peer.writeToSocket(msg);
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
