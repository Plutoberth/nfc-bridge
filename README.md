# nfc-bridge

A phone app that can bridge NFC traffic over the internet. It is (mostly) useful for bridging smart cards, as cards with a secure element may not be copied.

Example usage:

1. Device A opens the app in "Card" mode.
2. Device B opens the app in "Card Reader" mode.
3. Device A communicates with a real card terminal.
4. Device B communicates with a real card.
5. Traffic is bridged, and the card terminal receives data from device A as if the card is physically there!
