document.addEventListener("DOMContentLoaded", function () {

    // ============================
    // Employee Growth
    // ============================

    const employeeCanvas = document.getElementById("employeeChart");

    if (employeeCanvas) {

        new Chart(employeeCanvas, {

            type: "line",

            data: {

                labels: [
                    "Jan",
                    "Feb",
                    "Mar",
                    "Apr",
                    "May",
                    "Jun",
                    "Jul",
                    "Aug",
                    "Sep",
                    "Oct",
                    "Nov",
                    "Dec"
                ],

                datasets: [{

                    label: "Employees",

                    data: [
                        5,
                        10,
                        15,
                        22,
                        30,
                        37,
                        42,
                        48,
                        55,
                        61,
                        70,
                        80
                    ],

                    borderWidth: 3,

                    fill: true,

                    tension: 0.4

                }]

            },

            options: {

                responsive: true,

                plugins: {

                    legend: {

                        display: true

                    }

                }

            }

        });

    }

    // ============================
    // Attendance Overview
    // ============================

    const attendanceCanvas =
        document.getElementById("attendanceChart");

    if (attendanceCanvas) {

        new Chart(attendanceCanvas, {

            type: "bar",

            data: {

                labels: [

                    "Present",
                    "Late",
                    "Absent"

                ],

                datasets: [{

                    label: "Employees",

                    data: [

                        90,
                        7,
                        3

                    ],

                    borderWidth: 2

                }]

            },

            options: {

                responsive: true

            }

        });

    }

    // ============================
    // Leave Overview
    // ============================

    const leaveCanvas =
        document.getElementById("leaveChart");

    if (leaveCanvas) {

        new Chart(leaveCanvas, {

            type: "pie",

            data: {

                labels: [

                    "Approved",
                    "Pending",
                    "Rejected"

                ],

                datasets: [{

                    data: [

                        18,
                        6,
                        3

                    ]

                }]

            },

            options: {

                responsive: true

            }

        });

    }

    // ============================
    // Payroll Overview
    // ============================

    const payrollCanvas =
        document.getElementById("payrollChart");

    if (payrollCanvas) {

        new Chart(payrollCanvas, {

            type: "bar",

            data: {

                labels: [

                    "Jan",
                    "Feb",
                    "Mar",
                    "Apr",
                    "May",
                    "Jun"

                ],

                datasets: [{

                    label: "Payroll (KSh)",

                    data: [

                        120000,
                        130000,
                        135000,
                        145000,
                        150000,
                        160000

                    ],

                    borderWidth: 2

                }]

            },

            options: {

                responsive: true

            }

        });

    }

});