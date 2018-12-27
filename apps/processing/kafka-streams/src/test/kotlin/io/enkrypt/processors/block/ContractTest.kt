package io.enkrypt.processors.block

import io.enkrypt.avro.common.ContractType
import io.enkrypt.common.extensions.data20
import io.enkrypt.common.extensions.ether
import io.enkrypt.common.extensions.gwei
import io.enkrypt.common.extensions.hexBuffer
import io.enkrypt.common.extensions.unsignedByteBuffer
import io.enkrypt.kafka.streams.models.StaticAddresses
import io.enkrypt.kafka.streams.models.StaticAddresses.EtherZero
import io.enkrypt.kafka.streams.processors.block.ChainEvents
import io.enkrypt.kafka.streams.processors.block.ChainEvents.contractCreate
import io.enkrypt.kafka.streams.processors.block.ChainEvents.contractDestroy
import io.enkrypt.kafka.streams.processors.block.ChainEvents.fungibleTransfer
import io.enkrypt.util.Blockchains.Coinbase
import io.enkrypt.util.Blockchains.Users.Bob
import io.enkrypt.util.SolidityContract
import io.enkrypt.util.StandaloneBlockchain
import io.enkrypt.util.TestContracts
import io.enkrypt.util.totalTxFees
import io.enkrypt.util.txFees
import io.kotlintest.shouldBe
import io.kotlintest.specs.BehaviorSpec

class ContractTest : BehaviorSpec() {

  private val premineBalances = mapOf(
    Bob.address.data20() to 20.ether()
  )

  private val bcConfig = StandaloneBlockchain.Config(
    gasLimit = 31000,
    gasPrice = 1.gwei().toLong(),
    premineBalances = premineBalances,
    coinbase = Coinbase.address.data20()!!
  )

  val bc by lazy { StandaloneBlockchain(bcConfig) }

  init {

    given("a contract with a self destruct function") {

      val contract = TestContracts.SELF_DESTRUCTS.contractFor("SelfDestruct")

      `when`("we instantiate it") {

        val tx = bc.submitContract(Bob, contract, gasLimit = 500_000)
        val contractAddress = tx.contractAddress.data20()

        val block = bc.createBlock()
        val chainEvents = ChainEvents.forBlock(block)

        then("there should be 3 chain events") {
          chainEvents.size shouldBe 3
        }

        then("there should be a fungible ether transfer for the coinbase") {
          chainEvents.first() shouldBe fungibleTransfer(
            EtherZero,
            Coinbase.address.data20()!!,
            (3.ether() + block.totalTxFees()).unsignedByteBuffer()!!
          )
        }

        then("there should be a transaction fee ether transfer") {
          chainEvents[1] shouldBe fungibleTransfer(
            Bob.address.data20()!!,
            EtherZero,
            block.txFees()[0].unsignedByteBuffer()!!
          )
        }

        then("there should be a contract creation event") {
          chainEvents[2] shouldBe contractCreate(
            ContractType.GENERIC,
            Bob.address.data20()!!,
            block.getHeader().getHash(),
            block.getTransactions().first().getHash(),
            contractAddress!!,
            contract.bin.hexBuffer()!!
          )
        }
      }

      `when`("we instantiate it with ether") {

        val tx = bc.submitContract(Bob, contract, gasLimit = 500_000, value = 10.gwei())
        val contractAddress = tx.contractAddress.data20()

        val block = bc.createBlock()
        val chainEvents = ChainEvents.forBlock(block)

        then("there should be 4 chain events") {
          chainEvents.size shouldBe 4
        }

        then("there should be a fungible ether transfer for the coinbase") {
          chainEvents.first() shouldBe fungibleTransfer(
            StaticAddresses.EtherZero,
            Coinbase.address.data20()!!,
            (3.ether() + block.totalTxFees()).unsignedByteBuffer()!!
          )
        }

        then("there should be a transaction fee ether transfer") {
          chainEvents[1] shouldBe fungibleTransfer(
            Bob.address.data20()!!,
            EtherZero,
            block.txFees()[0].unsignedByteBuffer()!!
          )
        }

        then("there should be a contract creation event") {
          chainEvents[2] shouldBe contractCreate(
            ContractType.GENERIC,
            Bob.address.data20()!!,
            block.getHeader().getHash(),
            block.getTransactions().first().getHash(),
            contractAddress!!,
            contract.bin.hexBuffer()!!
          )
        }

        then("there should be a fungible transfer event for the ether sent") {
          chainEvents[3] shouldBe fungibleTransfer(
            Bob.address.data20()!!,
            contractAddress!!,
            10.gwei().unsignedByteBuffer()!!
          )
        }
      }

      `when`("we ask it to self destruct") {

        val tx = bc.submitContract(Bob, contract, gasLimit = 500_000, value = 35.gwei())
        val contractAddress = tx.contractAddress.data20()!!

        bc.createBlock()

        bc.callFunction(Bob, contractAddress, contract, "destroy")

        val block = bc.createBlock()
        val chainEvents = ChainEvents.forBlock(block)

        then("there should be 4 chain events") {
          chainEvents.size shouldBe 4
        }

        then("there should be a fungible ether transfer for the coinbase") {
          chainEvents.first() shouldBe fungibleTransfer(
            StaticAddresses.EtherZero,
            Coinbase.address.data20()!!,
            (3.ether() + block.totalTxFees()).unsignedByteBuffer()!!
          )
        }

        then("there should be a transaction fee ether transfer") {
          chainEvents[1] shouldBe fungibleTransfer(
            Bob.address.data20()!!,
            EtherZero,
            block.txFees()[0].unsignedByteBuffer()!!
          )
        }

        then("there should be a contract destruct event") {
          chainEvents[2] shouldBe contractDestroy(
            block.getHeader().getHash(),
            block.getTransactions().first().getHash(),
            contractAddress
          )
        }

        then("there should be a fungible transfer to the sender with the contract balance") {
          chainEvents[3] shouldBe fungibleTransfer(
            contractAddress,
            Bob.address.data20()!!,
            35.gwei().unsignedByteBuffer()!!
          )
        }
      }
    }

    given("a ping pong contract with delegating calls") {

      val contract = TestContracts.PING_PONG.contractFor("PingPong")

      val pingTx = bc.submitContract(Bob, contract, gasLimit = 500_000, value = 1.ether())
      val pingAddress = SolidityContract.contractAddress(Bob, pingTx.nonce).data20()!!

      val pongTx = bc.submitContract(Bob, contract, gasLimit = 500_000, value = 1.ether())
      val pongAddress = SolidityContract.contractAddress(Bob, pongTx.nonce).data20()!!

      bc.createBlock()

      `when`("we trigger a series of cascading calls") {

        bc.callFunction(Bob, pingAddress, contract, "start", null, null, null, pongAddress.bytes())

        val block = bc.createBlock()
        val chainEvents = ChainEvents.forBlock(block)

        then("there should be 3 chain events") {
          chainEvents.size shouldBe 3
        }

        then("there should be a fungible ether transfer for the coinbase") {
          chainEvents.first() shouldBe fungibleTransfer(
            StaticAddresses.EtherZero,
            Coinbase.address.data20()!!,
            (3.ether() + block.totalTxFees()).unsignedByteBuffer()!!
          )
        }

      }

    }
  }
}
