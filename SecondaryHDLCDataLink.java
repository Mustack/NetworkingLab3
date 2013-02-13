import java.io.IOException;
import java.util.ArrayList;

// Data Link Layer Entity for Secondary Station
// Uses the HDLC protocol for communication over a multipoint link
// Assumptions
//    Normal Response Mode operation over multi-point link (simulated using PhysicalLayer class over Sockets)
//    Use 3-bit sequence numbers
// Not Supported:
//    FSC checking
//    Bit stuffing (frames are transmitted as strings)
//  Frames implemented:
//     Command Frames:
//        NRM: 
//        DISC: 
//     Response Frames:
//        UA: 
//     Command/Response Frames:
//        I: maximum length of data field is 64 bytes.
//        RR: 

public class SecondaryHDLCDataLink 
{
	// Private instance variables
	private PhysicalLayer physicalLayer; // for sending/receiving frames
	private int stationAdr; // Station address - not used for the primary station
	// Data for multiple connections in the case of the primary station
	// For the secondary station, used values at index 0
	private int vs;
	private int vr;
	private int rhsWindow; // right hand side of window.  
	private int windowSize; // transmit window size. reception window size is 1.
	private ArrayList<String> frameBuffer;
	
	// Constructor
	public SecondaryHDLCDataLink(int adr)
	{
		physicalLayer = new PhysicalLayer();	
		stationAdr = adr;
	    vs = 0;
	    vr = 0;
	    windowSize = 4;  //
	    frameBuffer = new ArrayList<String>();
	    rhsWindow = vs+windowSize; // seq # < rhsWindow
	}
	
	public void close() throws IOException
	{
		physicalLayer.close();
	}
	
	/*----------------------------------------------------------
	 *  Connection Service
	 *-----------------------------------------------------------*/
	// This is a confirmed service, i.e. the return value reflects results from the confirmation

	public Result dlConnectIndication()
	{  // Receive NRM command frame
		Result.ResultCode cd = Result.ResultCode.SrvSucessful;
		int adr = 0;
		String retStr = null;
		// Wait for UA response frame
		String frame = getFrame(true);  // true - wait for frame
		adr = BitString.bitStringToInt(frame.substring(HdlcDefs.ADR_START,HdlcDefs.ADR_END));
		// Check if frame is U-frame
		String type = frame.substring(HdlcDefs.TYPE_START, HdlcDefs.TYPE_END);
		if(type.equals(HdlcDefs.U_FRAME) == false)
		{
			cd = Result.ResultCode.UnexpectedFrameReceived;
			retStr = type;
		}	
		else
		{
			String uframe = frame.substring(HdlcDefs.M1_START, HdlcDefs.M1_END) +
			                frame.substring(HdlcDefs.M2_START, HdlcDefs.M2_END);
			if(uframe.equals(HdlcDefs.SNRM)==false)
			{
				cd = Result.ResultCode.UnexpectedUFrameReceived;
				retStr = uframe;
			}
			else System.out.println("Data Link Layer: received SNRM frame >"+BitString.displayFrame(frame)+"<");
		}
		return(new Result(cd, adr, retStr));		
	}

	public Result dlConnectResponse()
	{
		Result.ResultCode cd = Result.ResultCode.SrvSucessful;
		// Check if room for additional connection
		String frame = HdlcDefs.FLAG+BitString.intToBitString(stationAdr,HdlcDefs.ADR_SIZE_BITS)+
		               HdlcDefs.U_FRAME+
		               HdlcDefs.UA_M1+HdlcDefs.P1+HdlcDefs.UA_M2+
		               HdlcDefs.FLAG;
		System.out.println("Data Link Layer: prepared UA frame >"+BitString.displayFrame(frame)+"<");
		physicalLayer.transmit(frame);
		vs=0;
		vr=0;
		return(new Result(cd, stationAdr, null));				
	}
	
	/*----------------------------------------------------------
	 *  Disconnect service - non-confirmed service
	 *-----------------------------------------------------------*/	
	
	public Result dlDisconnectIndication()
	{   // Disconnection to secondary.
		Result.ResultCode cd = Result.ResultCode.SrvSucessful;
		int adr = 0;
		String retStr = null;
		// Wait for DISC frame
		String frame = getFrame(true);  // true - wait for frame
		adr = BitString.bitStringToInt(frame.substring(HdlcDefs.ADR_START,HdlcDefs.ADR_END));
		// Check if frame is U-frame
		String type = frame.substring(HdlcDefs.TYPE_START, HdlcDefs.TYPE_END);
		if(type.equals(HdlcDefs.U_FRAME) == false)
		{
			cd = Result.ResultCode.UnexpectedFrameReceived;
			retStr = type;
		}	
		else
		{
			String uframe = frame.substring(HdlcDefs.M1_START, HdlcDefs.M1_END) +
			                frame.substring(HdlcDefs.M2_START, HdlcDefs.M2_END);
			if(uframe.equals(HdlcDefs.DISC)==false)
			{
				cd = Result.ResultCode.UnexpectedUFrameReceived;
				retStr = uframe;
			}
			else System.out.println("Data Link Layer: received DISC frame >"+BitString.displayFrame(frame)+"<");
		}
		return(new Result(cd, adr, retStr));		
	}	

	/*----------------------------------------------------------
	 *  Data service - non-confirmed service
	 *-----------------------------------------------------------*/	

	public Result dlDataRequest(String sdu)
	{
		String frame; // For building frames
		Result.ResultCode cd = Result.ResultCode.SrvSucessful;

		// Wait for poll - need an RR with P bit - 1

		frame = getRRFrame(true);
		if (frame.charAt(HdlcDefs.PF_IX)=='0') return null; //if it's not a poll
		
		
		/*Completer cette partie*/
		
		// Send the SDU
		// After each transmission, check for an ACK (RR)
		// Use a sliding window
		// Reception will be go back-N
		String [] dataArr = BitString.splitString(sdu, HdlcDefs.MAX_DATA_SIZE_BYTES);
		// Convert the strings into bitstrings
		for(int ix=0 ; ix < dataArr.length; ix++)
			dataArr[ix] = BitString.stringToBitString(dataArr[ix]);

		//initialize values for the loop
		int i = 0;
		int nombreDeTramesAcquite;
		int nr;
		String sendFrame;
		
		// Loop to transmit frames
		/*Completer la boucle*/
		while(i < dataArr.length)//toujours des trames a envoyer
		{
			// Send frame if window not closed and data not all transmitted
			if(vs != rhsWindow)
			{
				//envoye trame
				sendFrame = dataArr[i++];
				vs = (vs + 1) % HdlcDefs.MAX_DATA_SIZE_BYTES;
				frameBuffer.add(sendFrame);
				displayDataXchngState("Data Link Layer: prepared and buffered I frame >"+BitString.displayFrame(sendFrame)+"<");
			}
			// Check for RR
			frame = getRRFrame(false); // just poll
			if ((frame != null) && (frame.charAt(HdlcDefs.PF_IX)=='0')) // have an ACK frame
			{
				nr = Integer.parseInt(frame.substring(HdlcDefs.NR_START, HdlcDefs.NR_END));
				nombreDeTramesAcquite = checkNr(nr, rhsWindow, windowSize);
				displayDataXchngState("received an RR frame (ack) >"+BitString.displayFrame(frame)+"<");
			}	
		}
		return(new Result(cd, 0, null));		
	}

	/*------------------------------------------------------------------------
	 * Helper Methods
	 *------------------------------------------------------------------------*/

	// Determines the number of frames acknowledged from the
	// acknowledge number nr.
	// Parameters
	// nr - received ack number - indicates next expected
	//      sequence number (nr can equal lhs - window is closed)
	// rhs - right hand side of window - seq number to the
	//       right of the last valid number that can be used
	// sz - size of the window
	private int checkNr(int nr, int rhs, int sz)
	{
		int lhs;	// repr�sente le c�t� gauche de la fen�tre (c'est-�-dire le premier num�ro de s�quence disponible)
		
		if ((rhs - sz) >= 0) {
			// Si lhs < rhs, alors on v�rifie que nr se situe entre ces deux valeurs.
			lhs = rhs - sz;
			if ((nr <= rhs) && (nr >= lhs)) {
				// Si nr est un num�ro de s�quence valide, on renvoie ce num�ro - lhs
				// (c'est-�-dire le nombre de trames qui ont �t� acquitt�es).
				return nr - lhs;
			} else {
				// Si nr n'est pas valide, on retourne simplement 0.
				return 0;
			}
		} else {
			// Puisque la liste de num�ros de s�quences est circulaire, alors il se peut que lhs > (HdlcDefs.SNUM_SIZE_COUNT / 2)
			// et que rhs < [(HdlcDefs.SNUM_SIZE_COUNT / 2) - 1]. On calcule d'abord la valeur de lhs.
			lhs = rhs + HdlcDefs.SNUM_SIZE_COUNT - sz;
			if ((nr <= rhs) || (nr >= lhs)) {
				// Si nr est un num�ro de s�quence valide, on translate la fen�tre et la valeur de nr,
				// pour ensuite trouver le nombre de trames acquitt�es de la m�me fa�on que ci-dessus.
				int newlhs = rhs;
				int newnr = (nr + sz) % HdlcDefs.SNUM_SIZE_COUNT;
				return newnr - newlhs;
			} else {
				// Si nr n'est pas valide, on retourne simplement 0.
				return 0;
			}
		}
	}
	
	// Helper method to get an RR-frame
	// If wait is true then wait until a frame
	// arrives (call getframe(true).
	// If false, return null if no frame
	// is available from the physical layer (call getframe(false)
	// or frame received is not an RR frame.
	private String getRRFrame(boolean wait)
	{
		String frame;	// la trame RR retourn�e (ou null si wait est false et qu'aucune trame RR n'est disponible)
		
		do
		{
			frame = getFrame(wait);		// r�cup�rer la trame
			if (frame == null) return frame; // arrive seulement si wait est false et qu'aucune trame RR n'est disponible.
			
			// Retourner la trame uniquement s'il s'agit d'une trame RR...
			if ((frame.substring(HdlcDefs.TYPE_START,HdlcDefs.TYPE_END).equals(HdlcDefs.S_FRAME))
					&& (frame.substring(HdlcDefs.S_START,HdlcDefs.S_END).equals(HdlcDefs.RR_SS)))
			{
				return frame;
			}
			// R�p�ter tant et aussi longtemps que wait est vrai et qu'une trame qui n'est pas de type RR est disponible
			// (si wait est true, la m�thode retourne aussit�t qu'une trame RR est trouv�e).
		} while (wait && frame != null);
		
		return frame;
	}

	// For displaying the status of variables used
	// in exchanging data between stations.
	private void displayDataXchngState(String msg)
	{
		int lhs; // left hand side of the window
		//compute lhs
		if( (rhsWindow-windowSize) >= 0) lhs = rhsWindow - windowSize;
		else lhs = rhsWindow - windowSize + HdlcDefs.SNUM_SIZE_COUNT;

		System.out.println("Data Link Layer: Station "+stationAdr+": "+msg);
		System.out.println("    v(s) = "+vs+", v(r) = "+vr+
				           ", Window: lhs="+lhs+" rhs="+rhsWindow+
				           ", Number frames buffered = "+frameBuffer.size());
	}

	// Waits for reception of frame
	// If wait is true, then wait for a frame to arrive,
	// otherwise just poll physical layer for a frame.
	// Returns null if no frame is received.
	private String getFrame(boolean wait)
	{
		// Only frames with this stations address is processed - others are ignored
		String frame = null;
		do
		{
			if(wait) frame = physicalLayer.receive(); // block on receive.
			else frame = physicalLayer.pollReceive();  // get frame from physical layer
			if(frame != null)
			{
				int adr = BitString.bitStringToInt(frame.substring(HdlcDefs.ADR_START, HdlcDefs.ADR_END));
				if(adr != stationAdr) frame = null;  // ignore strings for other destinations			
			}
		} while(frame == null && wait);
		//if(frame != null) System.out.println("Data Link Layer: Received frame >"+BitString.displayFrame(frame)+"<");
		return(frame);
	}
		
}
