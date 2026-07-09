document.addEventListener("DOMContentLoaded", function () {

    const canvas = document.getElementById("employeeChart");

    if (!canvas) return;

    new Chart(canvas, {
        type: "line",
        data: {
            labels: ["Jan", "Feb", "Mar", "Apr", "May", "Jun"],
            datasets: [{
                label: "Employees",
                data: [5, 9, 12, 18, 22, 30],
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

});