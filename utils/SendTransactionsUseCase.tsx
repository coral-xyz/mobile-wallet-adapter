import {
  Connection,
  VersionedTransaction,
  TransactionSignature,
  SendOptions,
  clusterApiUrl,
} from '@solana/web3.js';
import {decode} from 'bs58';

export class SendTransactionsUseCase {
  static readonly SIGNATURE_LEN = 64;
  static readonly PUBLIC_KEY_LEN = 32;

  static async sendSignedTransactions(
    signedTransactions: Array<Uint8Array>,
    minContextSlot: number | undefined,
  ): Promise<number[][]> {
    const connection = new Connection(clusterApiUrl('testnet'), 'finalized');
    const signatures: number[][] = await Promise.all(
      signedTransactions.map(async byteArray => {
        const transaction: VersionedTransaction =
          VersionedTransaction.deserialize(byteArray);

        const sendOptions: SendOptions = {
          minContextSlot: minContextSlot,
          preflightCommitment: 'processed',
        };
        console.log(transaction);
        const signature: TransactionSignature =
          await connection.sendTransaction(transaction, sendOptions); // here
        const decoded = decode(signature);
        console.log('decoded');
        console.log(decoded);
        return Array.from(decoded);
      }),
    );

    return signatures;
  }
}
