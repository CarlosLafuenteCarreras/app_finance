package com.carloslafuente.finance.data.backup

import com.carloslafuente.finance.data.local.AccountEntity
import com.carloslafuente.finance.data.local.AccountKind
import com.carloslafuente.finance.data.local.AssetEntity
import com.carloslafuente.finance.data.local.AssetType
import com.carloslafuente.finance.data.local.TransactionEntity
import com.carloslafuente.finance.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class BackupTest {

    @Test
    fun `a backup read back is identical to what was exported`() {
        val backup = Backup(
            accounts = listOf(AccountEntity(1, "Trade \"Republic\"", AccountKind.BROKER), AccountEntity(2, "Banco", AccountKind.BANK)),
            assets = listOf(
                AssetEntity(7, "MSCI World", AssetType.ETF, "IE00B4L5Y983"),
                AssetEntity(8, "Letra del Tesoro", AssetType.BOND, isin = null),
            ),
            transactions = listOf(
                TransactionEntity(
                    id = 3,
                    accountId = 1,
                    assetId = 7,
                    type = TransactionType.BUY,
                    date = LocalDate.parse("2025-03-14"),
                    quantity = BigDecimal("0.123456"),
                    price = BigDecimal("131.7500"),
                    fee = BigDecimal("1.00"),
                    note = "aportación mensual",
                ),
                TransactionEntity(
                    id = 4,
                    accountId = 2,
                    type = TransactionType.INTEREST,
                    date = LocalDate.parse("2025-04-01"),
                    amount = BigDecimal("12.34"),
                    tax = BigDecimal("2.34"),
                ),
            ),
        )

        assertEquals(backup, parseBackup(backup.toJson()))
    }

    @Test
    fun `a file that is not a backup is rejected`() {
        assertThrows(Exception::class.java) { parseBackup("""{"version":1,"accounts":[]}""") }
        assertThrows(Exception::class.java) { parseBackup("""{"version":99,"accounts":[],"assets":[],"transactions":[]}""") }
        assertThrows(Exception::class.java) { parseBackup("not json") }
    }
}
