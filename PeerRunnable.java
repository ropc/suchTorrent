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
			if (peer == this.peer && peerIsLegit == true) {
				WriteRunnable newWriteRunnable = new WriteRunnable(this.peer);
				this.peer.writeThread = newWriteRunnable;
				(new Thread(newWriteRunnable)).start();
				this.peer.startReading();
			}
		}
	}
   
   public static class HS_StartAndReadRunnable extends PeerRunnable {
      protected Handshake hs;

		public HS_StartAndReadRunnable(Peer peerToManage, Handshake peer_hs) {
			peer = peerToManage;
         hs = peer_hs;
		}
      public void run(Handshake hs){
         
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
							if (message.type == Message.REQUEST || message.type == Message.PIECE)
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
