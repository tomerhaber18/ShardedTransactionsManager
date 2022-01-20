package com.example.api.controller
import business_logic.TransactionManager
import com.example.api.SpringBootBoilerplateApplication1
import org.springframework.web.bind.annotation.*

/**
 * Controller for REST API endpoints
 */

@RestController
class TransactionsController {
    private var TransactionManager: TransactionManager? = null

    init {
        TransactionManager = SpringBootBoilerplateApplication1.a.TransactionManager
    }

    @GetMapping("/transactions/unspent/{address}")
    fun getUnspentTransactions(@PathVariable("address") address: String): String? {
        return TransactionManager?.controllerGetUnspentTransactions(address)
    }

    @GetMapping("/transactions/history/{address}")
    fun getAddressHistoryTransactions(@PathVariable("address") address: String,
                                      @RequestParam(required = false) number_of_records: Int?): String? {
        return TransactionManager?.controllerGetAddressHistoryTransactions(address, number_of_records)
    }

    @GetMapping("/transactionsHistory")
    fun getHistoryTransactions(@RequestParam(required = false) number_of_records: Int?) : String? {
        return TransactionManager?.controllerGetHistoryTransactions(number_of_records)
    }

    @PostMapping("/transactions/newTx")
    fun NewTransaction(@RequestBody new_transaction: String) : String? {
        return TransactionManager?.controllerNewTransaction(new_transaction)
    }

    @PostMapping("/transactions/send")
    fun SendAmount(@RequestBody tr: String) : String? {
        return TransactionManager?.controllerSendAmount(tr)
    }

    @PostMapping("/transactions/newTxs")
    fun NewTransactions(@RequestBody new_transactions: String) : String? {
        return TransactionManager?.controllerNewTransactions(new_transactions)
    }
}