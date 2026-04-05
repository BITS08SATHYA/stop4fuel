package com.stopforfuel.app.ui.navigation

sealed class Routes(val route: String) {
    data object Login : Routes("login")
    data object Home : Routes("home")
    data object CreateInvoice : Routes("create_invoice")
    data object ShiftInvoices : Routes("shift_invoices")
    data object StartPumpSession : Routes("start_pump_session")
    data object EndPumpSession : Routes("end_pump_session/{sessionId}") {
        fun withId(id: Long) = "end_pump_session/$id"
    }
    data object CustomerList : Routes("customer_manage")
    data object CustomerDetail : Routes("customer_detail/{customerId}") {
        fun withId(id: Long) = "customer_detail/$id"
    }
    data object EmployeeManage : Routes("employee_manage")
}
