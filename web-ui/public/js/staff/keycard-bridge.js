/**
 * RFID Keycard Encoding & Registration Card Hardware Bridge
 * Location: web-ui/js/staff/keycard-bridge.js
 * 
 * Establishes a lightweight WebSocket bridge with the local hardware daemon
 * running at ws://localhost:8089/encoder to encode physical RFID door keycards
 * and trigger 5-star Registration Card printing upon front-desk check-in completion.
 */

export class KeycardEncoderBridge {
  constructor(bridgeUrl = 'ws://localhost:8089/encoder') {
    this.bridgeUrl = bridgeUrl;
    this.socket = null;
  }

  /**
   * Connect to the local WebSocket hardware daemon.
   */
  connect() {
    return new Promise((resolve, reject) => {
      try {
        const WebSocketClass = typeof WebSocket !== 'undefined' ? WebSocket : (globalThis.WebSocket || null);
        if (!WebSocketClass) {
          return reject(new Error('WebSocket is not supported in this runtime environment.'));
        }
        this.socket = new WebSocketClass(this.bridgeUrl);

        this.socket.onopen = () => resolve(true);
        this.socket.onerror = (err) => reject(new Error('Local Keycard Encoder Bridge offline.'));
      } catch (err) {
        reject(err);
      }
    });
  }

  /**
   * Send command to encode physical RFID room keycard.
   * 
   * @param {string|number} roomNumber - Hotel room number (e.g. 1402)
   * @param {string} checkOutDate - Departure date/time ISO string
   * @param {string} keyLevel - Access tier ('GUEST', 'VIP', 'SUITE')
   * @returns {Promise<Object>} Encoder response envelope
   */
  async encodeCard(roomNumber, checkOutDate, keyLevel = 'GUEST') {
    const wsOpen = typeof WebSocket !== 'undefined' ? WebSocket.OPEN : 1;
    if (!this.socket || this.socket.readyState !== wsOpen) {
      await this.connect();
    }

    const command = {
      action: 'ENCODE_KEYCARD',
      timestamp: Date.now(),
      payload: {
        room: String(roomNumber),
        validUntil: checkOutDate,
        level: keyLevel,
        encoderIndex: 1
      }
    };

    return new Promise((resolve, reject) => {
      this.socket.onmessage = (event) => {
        try {
          const resp = typeof event.data === 'string' ? JSON.parse(event.data) : event.data;
          if (resp.status === 'SUCCESS' || resp.status === 'ENCODED_SUCCESS') {
            resolve(resp);
          } else {
            reject(new Error(resp.errorMessage || resp.message || 'Encoding failed on hardware.'));
          }
        } catch (e) {
          reject(e);
        }
      };
      this.socket.send(JSON.stringify(command));
    });
  }

  /**
   * Send command to print 5★ Guest Registration Card.
   * 
   * @param {string|number} bookingId - PMS Booking ID
   * @param {string|number} roomNumber - Assigned Room Number
   * @param {Object} guestData - Guest metadata (name, VIP tier, dates)
   * @returns {Promise<Object>} Print daemon response envelope
   */
  async printRegistrationCard(bookingId, roomNumber, guestData = {}) {
    const wsOpen = typeof WebSocket !== 'undefined' ? WebSocket.OPEN : 1;
    if (!this.socket || this.socket.readyState !== wsOpen) {
      await this.connect();
    }

    const command = {
      action: 'PRINT_REGISTRATION_CARD',
      timestamp: Date.now(),
      payload: {
        bookingId: String(bookingId),
        roomNumber: String(roomNumber),
        guestName: guestData.guestName || guestData.fullName || 'Valued Guest',
        checkIn: guestData.checkInDate || guestData.checkIn || new Date().toISOString(),
        checkOut: guestData.checkOutDate || guestData.checkOut || '',
        vipTier: guestData.vipTier || guestData.tier || 'Standard',
        printerIndex: 1
      }
    };

    return new Promise((resolve, reject) => {
      this.socket.onmessage = (event) => {
        try {
          const resp = typeof event.data === 'string' ? JSON.parse(event.data) : event.data;
          if (resp.status === 'SUCCESS' || resp.status === 'PRINTED_SUCCESS') {
            resolve(resp);
          } else {
            reject(new Error(resp.errorMessage || resp.message || 'Printing failed on hardware.'));
          }
        } catch (e) {
          reject(e);
        }
      };
      this.socket.send(JSON.stringify(command));
    });
  }

  /**
   * Complete check-in dispatch: encodes keycard and prints registration card.
   * 
   * @param {Object} checkInPayload - Check-in details
   * @returns {Promise<Object>} Combined execution summary
   */
  async completeCheckInProcess({ bookingId, roomNumber, checkOutDate, keyLevel = 'GUEST', guestData = {} } = {}) {
    const encodeResult = await this.encodeCard(roomNumber, checkOutDate, keyLevel);
    const printResult = await this.printRegistrationCard(bookingId, roomNumber, guestData);
    return {
      status: 'SUCCESS',
      success: true,
      bookingId,
      roomNumber,
      encodeResult,
      printResult
    };
  }

  /**
   * Gracefully close the WebSocket connection.
   */
  disconnect() {
    if (this.socket) {
      try {
        this.socket.close();
      } catch (_) {}
      this.socket = null;
    }
  }
}
