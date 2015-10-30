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

	public static class WriteRunnable extends PeerRunnable {
		private Boolean running;
		protected WriteRunnable(Peer peerToManage) {
			peer = peerToManage;
			running = false;
		}

		public void run() {
			running = true;
			try {
				while (running == true) {
					PeerEvent<? extends EventPayload> event = peer.eventQueue.poll(90, TimeUnit.SECONDS);
					if (event != null) {
						if (event.type == PeerEvent.Type.MESSAGE_TO_SEND && event.payload instanceof MessageData)
							peer.writeToSocket((MessageData)event.payload);
						else if (event.type == PeerEvent.Type.SHUTDOWN)
							running = false;
					} else {
						peer.writeToSocket(new MessageData(Message.KEEPALIVE));
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}