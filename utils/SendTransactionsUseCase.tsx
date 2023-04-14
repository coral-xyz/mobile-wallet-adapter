import {
  Connection,
  VersionedTransaction,
  TransactionSignature,
  SendOptions,
} from '@solana/web3.js';
import {decode} from 'bs58';

export class SendTransactionsUseCase {
  static readonly SIGNATURE_LEN = 64;
  static readonly PUBLIC_KEY_LEN = 32;

  static async sendSignedTransactions(
    signedTransactions: Array<Uint8Array>,
    minContextSlot: number | undefined,
  ): Promise<Uint8Array[]> {
    const connection = new Connection(
      'https://api.devnet.solana.com',
      'confirmed',
    );
    const signatures: Uint8Array[] = await Promise.all(
      signedTransactions.map(async byteArray => {
        const transaction: VersionedTransaction =
          VersionedTransaction.deserialize(byteArray);

        const sendOptions: SendOptions = {
          minContextSlot: minContextSlot,
        };
        const signature: TransactionSignature =
          await connection.sendTransaction(transaction, sendOptions); // here

        return decode(signature);
      }),
    );

    return signatures;
  }
}
